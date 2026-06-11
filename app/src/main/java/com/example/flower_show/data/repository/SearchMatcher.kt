package com.example.flower_show.data.repository

import com.example.flower_show.model.VideoItem
import kotlin.math.max

/**
 * Strategy interface for matching videos against a search query.
 * Each implementation defines a different scoring approach.
 */
fun interface SearchMatcher {
    /** Return a score [0.0, 1.0] where 0 = no match, 1 = perfect match. */
    fun score(video: VideoItem, query: String): Float
}

/**
 * Default matching strategy: weighted keyword contains matching.
 *
 * Scoring:
 *   - Title exact match:       1.0
 *   - Title contains query:    0.7
 *   - Tag contains query:      0.5 per matching tag
 *   - Recommend word contains: 0.3 per matching word
 *
 * Threshold: scores < 0.3 are discarded.
 */
class WeightedContainsMatcher(private val threshold: Float = 0.3f) : SearchMatcher {
    override fun score(video: VideoItem, query: String): Float {
        val lower = query.lowercase().trim()
        if (lower.isEmpty()) return 0f

        var score = 0f
        val titleLower = video.title.lowercase()
        if (titleLower == lower) score += 1.0f
        else if (titleLower.contains(lower)) score += 0.7f
        score += video.tags.count { it.lowercase().contains(lower) } * 0.5f
        score += video.recommendWords.count { it.lowercase().contains(lower) } * 0.3f
        score += video.contentSearches.count { it.lowercase().contains(lower) } * 0.45f

        return if (score >= threshold) score else 0f
    }
}

/**
 * Hybrid matcher used by the app by default.
 *
 * It keeps exact/contains matching as the strongest signal, then adds typo tolerance
 * and lightweight semantic expansion for short Chinese queries such as "脆皮虾".
 * This is deterministic local matching, not a neural embedding model.
 */
class HybridSearchMatcher(
    private val lexical: SearchMatcher = WeightedContainsMatcher(),
    private val fuzzy: SearchMatcher = LevenshteinDistanceMatcher(
        threshold = 0.45f,
        fuzziness = 0.25f,
    ),
    private val semantic: SearchMatcher = SemanticKeywordMatcher(),
) : SearchMatcher {
    override fun score(video: VideoItem, query: String): Float {
        return maxOf(
            lexical.score(video, query),
            fuzzy.score(video, query) * 0.85f,
            semantic.score(video, query),
        )
    }
}

/**
 * Lightweight semantic keyword matcher.
 *
 * Good enough for offline MVP search: it understands useful Chinese n-grams and a
 * small domain vocabulary while avoiding very broad matches such as all food videos.
 */
class SemanticKeywordMatcher(
    private val threshold: Float = 0.18f,
) : SearchMatcher {
    override fun score(video: VideoItem, query: String): Float {
        val cleanQuery = query.normalizeSearchText()
        if (cleanQuery.isEmpty()) return 0f

        val title = video.title.normalizeSearchText()
        val tags = video.tags.joinToString(" ").normalizeSearchText()
        val recommendations = video.recommendWords.joinToString(" ").normalizeSearchText()
        val contentSearches = video.contentSearches.joinToString(" ").normalizeSearchText()

        var score = 0f
        if (title == cleanQuery) score += 1.0f
        else if (title.contains(cleanQuery)) score += 0.85f

        val terms = expandQuery(cleanQuery)
        score += terms.sumMatchedBy(title, fieldWeight = 0.32f)
        score += terms.sumMatchedBy(tags, fieldWeight = 0.22f)
        score += terms.sumMatchedBy(recommendations, fieldWeight = 0.16f)
        score += terms.sumMatchedBy(contentSearches, fieldWeight = 0.26f)

        val capped = score.coerceAtMost(0.95f)
        return if (capped >= threshold) capped else 0f
    }

    private fun expandQuery(query: String): List<QueryTerm> {
        val terms = linkedMapOf<String, Float>()
        fun add(value: String, weight: Float) {
            val clean = value.normalizeSearchText()
            if (clean.isBlank()) return
            if (clean.length == 1 && clean !in usefulSingleCharacters) return
            terms[clean] = max(terms[clean] ?: 0f, weight)
        }

        add(query, 1.0f)
        query.split(searchSeparators)
            .filter { it.isNotBlank() }
            .forEach { add(it, 0.9f) }

        if (query.any(Char::isCjk)) {
            if (query.length in 2..6) {
                query.windowed(size = 2, step = 1).forEach { add(it, 0.82f) }
            }
            if (query.length <= 4) {
                query.forEach { add(it.toString(), 0.60f) }
            }
        }

        semanticExpansions.forEach { group ->
            if (group.triggers.any { query.contains(it) }) {
                group.expansions.forEach { add(it, 0.38f) }
            }
        }

        return terms.map { (value, weight) -> QueryTerm(value, weight) }
    }

    private fun List<QueryTerm>.sumMatchedBy(text: String, fieldWeight: Float): Float {
        if (text.isBlank()) return 0f
        return filter { term -> text.contains(term.value) }
            .sumOf { term -> (term.weight * fieldWeight).toDouble() }
            .toFloat()
    }

    private data class QueryTerm(
        val value: String,
        val weight: Float,
    )

    private data class ExpansionGroup(
        val triggers: List<String>,
        val expansions: List<String>,
    )

    private companion object {
        val searchSeparators = Regex("[\\s#，,。.!！？?、｜|/_:;；（）()【】\\[\\]「」『』]+")
        val usefulSingleCharacters = setOf("虾", "蟹", "鱼", "鸡", "肉", "猫", "狗", "球", "歌")
        val semanticExpansions = listOf(
            ExpansionGroup(
                triggers = listOf("虾", "虾滑", "虾仁", "海鲜"),
                expansions = listOf("虾滑", "虾仁", "海鲜", "鲜虾"),
            ),
            ExpansionGroup(
                triggers = listOf("脆皮", "酥脆", "香酥", "炸"),
                expansions = listOf("酥脆", "香酥", "焦香", "炸"),
            ),
            ExpansionGroup(
                triggers = listOf("美食", "做法", "教程", "家常菜", "做饭", "做菜", "炒菜", "下厨", "料理", "菜谱", "吃饭"),
                expansions = listOf("美食", "美食教程", "家常菜", "下饭菜", "快手菜", "做法", "菜谱", "家常小炒"),
            ),
        )
    }
}

/**
 * Levenshtein-distance-based fuzzy matcher for typo tolerance.
 *
 * Computes edit distance between query and title/tags/recommendWords.
 * Only falls back to fuzzy matching when exact/contains fails.
 */
class LevenshteinDistanceMatcher(
    private val threshold: Float = 0.3f,
    private val fuzziness: Float = 0.3f,
) : SearchMatcher {
    override fun score(video: VideoItem, query: String): Float {
        val lower = query.lowercase().trim()
        if (lower.isEmpty()) return 0f

        var score = 0f
        val titleLower = video.title.lowercase()

        if (titleLower == lower) { score += 1.0f }
        else if (titleLower.contains(lower)) { score += 0.7f }
        else {
            val sim = levenshteinSimilarity(titleLower, lower)
            if (sim >= 1.0f - fuzziness) score += 1.0f * sim
        }

        score += video.tags.count { tag ->
            val tagLower = tag.lowercase()
            tagLower.contains(lower) || levenshteinSimilarity(tagLower, lower) >= 1.0f - fuzziness
        } * 0.5f

        score += video.recommendWords.count { word ->
            val wordLower = word.lowercase()
            wordLower.contains(lower) || levenshteinSimilarity(wordLower, lower) >= 0.7f
        } * 0.3f
        score += video.contentSearches.count { word ->
            val wordLower = word.lowercase()
            wordLower.contains(lower) || levenshteinSimilarity(wordLower, lower) >= 0.7f
        } * 0.45f

        return if (score >= threshold) score else 0f
    }

    companion object {
        fun levenshteinDistance(a: String, b: String): Int {
            val m = a.length; val n = b.length
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                    else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
            return dp[m][n]
        }

        fun levenshteinSimilarity(a: String, b: String): Float {
            val dist = levenshteinDistance(a, b)
            val maxLen = max(a.length, b.length)
            if (maxLen == 0) return 1.0f
            return 1.0f - dist.toFloat() / maxLen
        }
    }
}

private fun String.normalizeSearchText(): String {
    return lowercase()
        .replace(Regex("\\s+"), " ")
        .trim(' ', '#', '，', ',', '。', '.', '！', '!', '？', '?', '、')
}

private fun Char.isCjk(): Boolean {
    return this in '\u4e00'..'\u9fff'
}
