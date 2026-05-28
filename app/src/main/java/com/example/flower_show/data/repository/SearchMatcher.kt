package com.example.flower_show.data.repository

import com.example.flower_show.model.VideoItem
import kotlin.math.max
import kotlin.math.min

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

        return if (score >= threshold) score else 0f
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
