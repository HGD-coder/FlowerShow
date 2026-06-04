package com.example.flower_show.ai

import com.example.flower_show.model.VideoItem

object SearchSuggestionEngine {
    private val genericGuesses = listOf(
        "api中转",
        "女武神m80",
        "舒克白shukba1",
        "焰狐龙梓兰强度",
        "谢蕾蕾",
        "美年大健康优惠",
        "小微光",
        "明日方舟怪猎联动",
        "梅西世界杯",
        "iPhone16评测",
        "猫咪搞笑合集",
        "云南旅游攻略2024",
        "周杰伦歌曲合集",
        "王者荣耀最新赛季",
        "火锅做法大全",
        "腹肌训练教程",
    )

    private val stopWords = setOf(
        "合集",
        "视频",
        "推荐",
        "教程",
        "精彩",
        "最新",
        "深度",
        "大全",
        "一个",
        "现在",
        "这个",
    )

    fun guessSearches(history: List<String>, page: Int, count: Int): List<String> {
        val inferredFromHistory = history.flatMap { keyword ->
            inferSearches(title = keyword, tags = emptyList(), count = 4)
        }
        val candidates = (inferredFromHistory + genericGuesses).distinct()
        if (candidates.isEmpty()) return emptyList()

        val start = (page.coerceAtLeast(0) * count) % candidates.size
        return (candidates.drop(start) + candidates.take(start)).take(count)
    }

    fun relatedSearch(video: VideoItem): String {
        val inferred = inferVideoSearches(video, count = 1).firstOrNull()
        return inferred ?: video.title.takeClean(18)
    }

    fun inferVideoSearches(video: VideoItem, count: Int = 10): List<String> {
        return inferSearches(
            title = video.title,
            tags = video.tags + video.recommendWords,
            count = count,
        )
    }

    fun inferSearches(
        title: String,
        tags: List<String>,
        count: Int = 10,
    ): List<String> {
        val normalizedTitle = title.normalizeKeyword()
        val normalizedTags = tags
            .map { it.normalizeKeyword() }
            .filter { it.length >= 2 && it !in stopWords }

        val suggestions = linkedSetOf<String>()
        if (normalizedTitle.isNotBlank()) suggestions += normalizedTitle.takeClean(18)

        normalizedTags.forEach { suggestions += it.takeClean(18) }
        suggestions += inferByTopic(normalizedTitle, normalizedTags)

        val primary = normalizedTags.firstOrNull()
            ?: extractPrimaryKeyword(normalizedTitle)
            ?: normalizedTitle.takeClean(8)

        if (primary.isNotBlank()) {
            suggestions += "${primary}精彩片段"
            suggestions += "${primary}相关视频"
            suggestions += "${primary}最新"
        }

        return suggestions
            .map { it.normalizeKeyword() }
            .filter { it.length >= 2 }
            .distinct()
            .take(count)
    }

    private fun inferByTopic(title: String, tags: List<String>): List<String> {
        val content = (title + " " + tags.joinToString(" ")).lowercase()
        return when {
            content.contains("博人") || content.contains("火影") || content.contains("忍者") -> listOf(
                "博人跨时空救援",
                "火影忍者博人传",
                "鸣人佐助名场面",
                "大筒木剧情解析",
            )

            content.contains("游戏") || content.contains("王者") || content.contains("电竞") -> listOf(
                "游戏实况解说",
                "上分英雄推荐",
                "最新赛季强度",
                "手游高光操作",
            )

            content.contains("iphone") || content.contains("手机") || content.contains("科技") -> listOf(
                "手机拍照评测",
                "新机发布对比",
                "数码开箱体验",
                "续航测试排行",
            )

            content.contains("梅西") || content.contains("足球") || content.contains("世界杯") -> listOf(
                "梅西世界杯",
                "足球精彩瞬间",
                "进球集锦",
                "巴萨经典比赛",
            )

            content.contains("猫") || content.contains("宠物") || content.contains("萌宠") -> listOf(
                "猫咪搞笑合集",
                "萌宠日常",
                "宠物用品推荐",
                "喵星人表情包",
            )

            content.contains("火锅") || content.contains("美食") || content.contains("做法") -> listOf(
                "火锅做法大全",
                "家常菜谱教程",
                "探店美食推荐",
                "蘸料配方",
            )

            content.contains("旅游") || content.contains("旅行") || content.contains("攻略") -> listOf(
                "自由行攻略",
                "旅行路线推荐",
                "拍照打卡地点",
                "民宿推荐",
            )

            else -> emptyList()
        }
    }

    private fun extractPrimaryKeyword(title: String): String? {
        return title
            .split(" ", "，", ",", "。", "！", "!", "？", "?", "#", "｜", "|", "-", "_")
            .map { it.normalizeKeyword() }
            .firstOrNull { it.length >= 2 && it !in stopWords }
            ?.takeClean(8)
    }

    private fun String.normalizeKeyword(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
            .trim(' ', '#', '，', ',', '。', '.', '！', '!', '？', '?')
    }

    private fun String.takeClean(maxLength: Int): String {
        return normalizeKeyword().let { value ->
            if (value.length <= maxLength) value else value.take(maxLength)
        }
    }
}
