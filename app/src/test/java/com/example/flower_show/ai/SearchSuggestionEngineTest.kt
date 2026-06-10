package com.example.flower_show.ai

import com.example.flower_show.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionEngineTest {
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
}
