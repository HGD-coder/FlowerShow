package com.example.flower_show.data.repository

import com.example.flower_show.model.VideoItem
import org.junit.Assert.*
import org.junit.Test

class SearchMatcherTest {

    private val weighted = WeightedContainsMatcher()
    private val fuzzy = LevenshteinDistanceMatcher()

    private val video = VideoItem(
        id = "v001",
        title = "梅西生涯十佳进球集锦",
        author = "足球天下",
        avatarUrl = "",
        videoUrl = "",
        tags = listOf("足球", "梅西", "进球", "巴萨", "西甲"),
        recommendWords = listOf("梅西生涯进球", "巴萨经典比赛", "足球精彩瞬间"),
    )

    // ── WeightedContainsMatcher tests ──

    @Test
    fun weighted_exactTitleMatch_returnsMaximum() {
        val score = weighted.score(video, "梅西生涯十佳进球集锦")
        assertTrue("Exact title match should score >= 1.0, got $score", score >= 1.0f)
    }

    @Test
    fun weighted_partialTitleMatch_returnsPartial() {
        val score = weighted.score(video, "梅西")
        assertTrue("Partial title match should score >= 0.7, got $score", score >= 0.7f)
    }

    @Test
    fun weighted_tagMatch_returnsTagScore() {
        val score = weighted.score(video, "足球")
        assertTrue("Tag match should score >= 0.5, got $score", score >= 0.5f)
    }

    @Test
    fun weighted_recommendWordMatch_returnsRecommendScore() {
        val score = weighted.score(video, "巴萨经典比赛")
        assertTrue("Recommend word match should score >= 0.3, got $score", score >= 0.3f)
    }

    @Test
    fun weighted_noMatch_returnsZero() {
        val score = weighted.score(video, "不存在的内容XYZ123")
        assertEquals(0f, score)
    }

    @Test
    fun weighted_belowThreshold_returnsZero() {
        val score = weighted.score(video, "完全无关词")
        assertEquals(0f, score)
    }

    @Test
    fun weighted_emptyQuery_returnsZero() {
        assertEquals(0f, weighted.score(video, ""))
        assertEquals(0f, weighted.score(video, "  "))
    }

    // ── LevenshteinDistanceMatcher tests ──

    @Test
    fun fuzzy_exactTitleMatch_returnsMaximum() {
        val score = fuzzy.score(video, "梅西生涯十佳进球集锦")
        assertTrue("Exact title match should score >= 1.0, got $score", score >= 1.0f)
    }

    @Test
    fun fuzzy_partialContains_returnsPartial() {
        val score = fuzzy.score(video, "梅西")
        assertTrue("Contains match should score >= 0.7, got $score", score >= 0.7f)
    }

    @Test
    fun fuzzy_oneCharTypoInLongTitle_stillMatches() {
        // 1 substitution in 11-char title → distance=1, similarity≈0.91 ≥ 0.7
        val score = fuzzy.score(video, "美西生涯十佳进球集锦")
        assertTrue("1-char typo in 11-char title should still match, got $score", score > 0f)
    }

    @Test
    fun fuzzy_noMatch_returnsZero() {
        val score = fuzzy.score(video, "完全不相关的长文本查询")
        assertEquals(0f, score)
    }

    @Test
    fun fuzzy_emptyQuery_returnsZero() {
        assertEquals(0f, fuzzy.score(video, ""))
    }

    // ── LevenshteinDistance utility tests ──

    @Test
    fun levenshteinDistance_identical() {
        assertEquals(0, LevenshteinDistanceMatcher.levenshteinDistance("abc", "abc"))
    }

    @Test
    fun levenshteinDistance_oneSubstitution() {
        assertEquals(1, LevenshteinDistanceMatcher.levenshteinDistance("abc", "abd"))
    }

    @Test
    fun levenshteinDistance_oneInsertion() {
        assertEquals(1, LevenshteinDistanceMatcher.levenshteinDistance("abc", "abcd"))
    }

    @Test
    fun levenshteinDistance_oneDeletion() {
        assertEquals(1, LevenshteinDistanceMatcher.levenshteinDistance("abcd", "abc"))
    }

    @Test
    fun levenshteinDistance_completelyDifferent() {
        assertEquals(3, LevenshteinDistanceMatcher.levenshteinDistance("abc", "xyz"))
    }

    @Test
    fun levenshteinSimilarity_perfect() {
        assertEquals(1.0f, LevenshteinDistanceMatcher.levenshteinSimilarity("hello", "hello"))
    }

    @Test
    fun levenshteinSimilarity_oneEditOutOfFour() {
        // "abcd" → "abce": 1 substitution, maxLen=4, similarity=1-1/4=0.75
        val sim = LevenshteinDistanceMatcher.levenshteinSimilarity("abcd", "abce")
        assertEquals(0.75f, sim)
    }

    @Test
    fun levenshteinSimilarity_twoEditsOutOfFour() {
        // "abcd" → "abef": 2 substitutions, maxLen=4, similarity=1-2/4=0.5
        val sim = LevenshteinDistanceMatcher.levenshteinSimilarity("abcd", "abef")
        assertEquals(0.5f, sim)
    }
}
