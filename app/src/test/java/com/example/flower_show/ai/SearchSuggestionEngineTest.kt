package com.example.flower_show.ai

import com.example.flower_show.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionEngineTest {
    @Test
    fun relatedSearchPrefersContentSearchFromVideoFrames() {
        val video = VideoItem(
            id = "food",
            title = "普通标题",
            author = "作者",
            avatarUrl = "",
            videoUrl = "",
            tags = listOf("美食"),
            contentSearches = listOf("青椒变酿虾", "懒人快手菜"),
        )

        val relatedSearch = SearchSuggestionEngine.relatedSearch(video)

        assertEquals("青椒变酿虾", relatedSearch)
    }

    @Test
    fun guessSearchesUsesContentCandidatesBeforeGenericGuesses() {
        val guesses = SearchSuggestionEngine.guessSearches(
            history = emptyList(),
            page = 0,
            count = 2,
            contentCandidates = listOf("旅行vlog", "游戏精彩操作"),
        )

        assertEquals(listOf("旅行vlog", "游戏精彩操作"), guesses)
    }

    @Test
    fun relatedSearchPrefersInferredMusicTopicOverExactTitle() {
        val video = VideoItem(
            id = "music",
            title = "51分钟抖音热门音乐合集 戴上耳机 效果更佳 #音乐 #音乐分享 #音乐推荐",
            author = "暂别",
            avatarUrl = "",
            videoUrl = "",
            tags = listOf("音乐", "音乐分享", "音乐推荐"),
            recommendWords = listOf("51分钟抖音热门音乐合集"),
        )

        val relatedSearch = SearchSuggestionEngine.relatedSearch(video)

        assertEquals("抖音热曲音乐合集", relatedSearch)
    }

    @Test
    fun relatedSearchDoesNotMirrorFirstRecommendWord() {
        val video = VideoItem(
            id = "pet",
            title = "今天被猫咪笑疯了",
            author = "萌宠号",
            avatarUrl = "",
            videoUrl = "",
            tags = listOf("猫咪", "宠物", "萌宠"),
            recommendWords = listOf("猫咪搞笑合集", "萌宠视频"),
        )

        val relatedSearch = SearchSuggestionEngine.relatedSearch(video)

        assertFalse(relatedSearch == "猫咪搞笑合集")
        assertTrue(relatedSearch.contains("萌宠") || relatedSearch.contains("宠物") || relatedSearch.contains("喵星人"))
    }

    @Test
    fun relatedSearchCandidatesSkipPlainTags() {
        val video = VideoItem(
            id = "game",
            title = "王者荣耀新赛季上分实况",
            author = "游戏号",
            avatarUrl = "",
            videoUrl = "",
            tags = listOf("游戏", "王者", "电竞"),
            recommendWords = listOf("王者荣耀最新赛季"),
        )

        val candidates = SearchSuggestionEngine.inferRelatedSearches(video, count = 5)

        assertFalse(candidates.contains("游戏"))
        assertFalse(candidates.contains("王者"))
        assertTrue(candidates.any { it.contains("上分") || it.contains("游戏") || it.contains("赛季") })
    }

    @Test
    fun guessSearchesUsesHistoryBeforeContentCandidates() {
        val guesses = SearchSuggestionEngine.guessSearches(
            history = listOf("iphone bgm"),
            page = 0,
            count = 3,
            contentCandidates = listOf("content candidate"),
        )

        assertTrue(guesses.first().contains("iphone"))
        assertFalse(guesses.first() == "content candidate")
    }

    @Test
    fun guessSearchesPaginatesCandidates() {
        val firstPage = SearchSuggestionEngine.guessSearches(
            history = emptyList(),
            page = 0,
            count = 2,
            contentCandidates = listOf("one", "two", "three"),
        )
        val secondPage = SearchSuggestionEngine.guessSearches(
            history = emptyList(),
            page = 1,
            count = 2,
            contentCandidates = listOf("one", "two", "three"),
        )

        assertEquals(listOf("one", "two"), firstPage)
        assertEquals("three", secondPage[0])
    }

    @Test
    fun guessSearchesWithZeroCountReturnsEmptyList() {
        val guesses = SearchSuggestionEngine.guessSearches(
            history = listOf("iphone"),
            page = -1,
            count = 0,
            contentCandidates = listOf("content"),
        )

        assertTrue(guesses.isEmpty())
    }

    @Test
    fun inferVideoSearchesIncludesRecommendWordsAndContentSearches() {
        val video = VideoItem(
            id = "video",
            title = "plain title",
            author = "author",
            avatarUrl = "",
            videoUrl = "",
            tags = listOf("tag topic"),
            recommendWords = listOf("recommend topic"),
            contentSearches = listOf("visual topic"),
        )

        val searches = SearchSuggestionEngine.inferVideoSearches(video, count = 10)

        assertTrue(searches.contains("plain title"))
        assertTrue(searches.contains("tag topic"))
        assertTrue(searches.contains("recommend topic"))
        assertTrue(searches.contains("visual topic"))
    }
}
