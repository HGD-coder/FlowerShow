package com.example.flower_show.data.local

import android.content.Context
import com.example.flower_show.config.NetworkConfig
import com.example.flower_show.ai.SearchSuggestionEngine
import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.model.AlbumSlide
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.model.VideoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * assets JSON 数据加载器。
 *
 * 这份文件负责把本地 assets 里的 JSON/JSONL 数据转换成项目里的模型对象：
 * - VideoItem
 * - ImageCardItem
 * - AlbumCardItem
 *
 * 它只做“读取和解析”，不负责分页、不负责搜索排序、不负责播放。
 * 分页和搜索在 FakeVideoRepository 里，播放在 VideoPlayerManager 里。
 *
 * 数据格式支持两种：
 * - JSON 数组：整个文件是 [...]
 * - JSONL：一行一个 JSON 对象
 *
 * 视频/图片素材统一由公共媒体网关提供（NetworkConfig.mediaBaseUrl，
 * 由 PUBLIC_GATEWAY_BASE_URL 派生）。
 */
object AssetJsonLoader {
    /**
     * 视频数据文件名。
     *
     * loadVideos 会优先读 video_data.json，读不到再尝试 video_data.jsonl。
     */
    private const val JSON_FILENAME = "video_data.json"

    /**
     * 图片/图集数据文件名。
     */
    private const val IMAGE_JSON_FILENAME = "image_data.json"

    /**
     * Gson 用来把 JSON 字符串解析成 Map/List。
     */
    private val gson = Gson()

    /**
     * 素材统一由公共媒体网关提供。本地 8081 素材服务器已不再使用。
     */
    private fun hostBaseUrl(): String = NetworkConfig.mediaBaseUrl

    /**
     * 根据视频 id 拼出媒体网关的视频地址。
     *
     * 例如 awemeId = "123"：
     * {gateway}/media/videos/123/video.mp4
     */
    private fun videoUrlFor(awemeId: String): String =
        "${hostBaseUrl()}/videos/$awemeId/video.mp4"

    /**
     * 把相对路径转换成完整媒体网关 URL。
     *
     * 例如：
     * videos/123/video_480p.mp4
     * -> {gateway}/media/videos/123/video_480p.mp4
     */
    private fun localAssetUrlFor(relativePath: String): String =
        "${hostBaseUrl()}/$relativePath"

    /**
     * 加载视频数据。
     *
     * 流程：
     * 1. 从 assets 读取 video_data.json；如果没有，再读 video_data.jsonl。
     * 2. 读取额外的内容搜索词 contentSearches。
     * 3. 根据文件开头判断格式：
     *    - "{" 开头：按 JSONL 解析
     *    - "[" 开头：按 JSON 数组解析
     * 4. 返回 List<VideoItem>。
     */
    fun loadVideos(context: Context): List<VideoItem> {
        return try {
            val content = readAssetFile(context, JSON_FILENAME)
                ?: readAssetFile(context, "video_data.jsonl")
                ?: return emptyList()

            val contentSearches = AssetSearchSuggestionLoader.loadVideoContentSearches(context)
            // removePrefix 剔除 BOM：带 BOM 的 JSON 既不匹配 "{" 也不匹配 "["，
            // 会静默解析为空列表。
            val trimmed = content.trim().removePrefix("\uFEFF")
            val videos = when {
                trimmed.startsWith("{") -> parseJsonl(trimmed, contentSearches)
                trimmed.startsWith("[") -> parseJsonArray(trimmed, contentSearches)
                else -> emptyList()
            }
            videos
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 加载图片/图集数据。
     *
     * 一张图片会解析成 ImageCardItem；
     * 多张图片会解析成 AlbumCardItem，里面的每一张图变成 AlbumSlide。
     */
    fun loadImageCards(context: Context): List<CardItem> {
        return try {
            val content = readAssetFile(context, IMAGE_JSON_FILENAME)
                ?: readAssetFile(context, "image_data.jsonl")
                ?: return emptyList()

            val trimmed = content.trim().removePrefix("\uFEFF")
            when {
                trimmed.startsWith("[") -> parseImageJsonArray(trimmed)
                trimmed.startsWith("{") -> parseImageJsonl(trimmed)
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== JSONL / JSON 数组解析入口 =====

    /**
     * 解析视频 JSONL。
     *
     * JSONL 是“一行一个 JSON 对象”的格式，所以这里逐行解析。
     */
    private fun parseJsonl(
        content: String,
        contentSearches: Map<String, List<String>>,
    ): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(trimmed, Map::class.java) as Map<String, Any>
                parseMediaCrawlerItem(raw, contentSearches)?.let { videos.add(it) }
            } catch (_: Exception) { }
        }
        return videos
    }

    /**
     * 解析视频 JSON 数组。
     *
     * 如果对象里有 aweme_id，说明是 MediaCrawler 原始格式；
     * 否则尝试按 App 自己的简化格式解析。
     */
    private fun parseJsonArray(
        content: String,
        contentSearches: Map<String, List<String>>,
    ): List<VideoItem> {
        @Suppress("UNCHECKED_CAST")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(content, type)
        return rawList.mapNotNull { raw ->
            if (raw.containsKey("aweme_id")) parseMediaCrawlerItem(raw, contentSearches)
            else parseAppFormatItem(raw, contentSearches)
        }
    }

    /**
     * 解析图片/图集 JSONL。
     */
    private fun parseImageJsonl(content: String): List<CardItem> {
        val cards = mutableListOf<CardItem>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(trimmed, Map::class.java) as Map<String, Any>
                parseImageCardItem(raw)?.let { cards.add(it) }
            } catch (_: Exception) { }
        }
        return cards
    }

    /**
     * 解析图片/图集 JSON 数组。
     */
    private fun parseImageJsonArray(content: String): List<CardItem> {
        @Suppress("UNCHECKED_CAST")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(content, type)
        return rawList.mapNotNull(::parseImageCardItem)
    }

    // ===== MediaCrawler 原生格式解析 =====

    /**
     * 把 MediaCrawler 的一条视频原始数据转换成 VideoItem。
     *
     * 你可以重点看字段映射：
     * - aweme_id -> id
     * - title/desc -> title
     * - nickname -> author
     * - liked_count/comment_count/... -> likes/comments/...
     * - source_keyword -> tags
     *
     * videoUrl 不直接从 JSON 取，而是根据 id 拼出本地 HTTP 地址。
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseMediaCrawlerItem(
        raw: Map<String, Any>,
        contentSearchesById: Map<String, List<String>>,
    ): VideoItem? {
        val id = raw.str("aweme_id") ?: return null
        val title = raw.str("title") ?: raw.str("desc") ?: return null
        val author = raw.str("nickname") ?: "未知作者"
        val avatarUrl = parseLocalOrRemoteUrl(raw.str("avatar"))
        val coverUrl = parseLocalOrRemoteUrl(raw.str("cover_url"))
        val coverThumbnailUrl = parseLocalOrRemoteUrl(raw.str("cover_thumbnail_url"))
        val likes = raw.int("liked_count")
        val comments = raw.int("comment_count")
        val collections = raw.int("collected_count")
        val shares = raw.int("share_count")
        val keyword = raw.str("source_keyword") ?: ""
        val tags = if (keyword.isNotEmpty()) listOf(keyword) else emptyList()
        val recommendWords = SearchSuggestionEngine.inferSearches(
            title = title,
            tags = tags,
            count = 10,
        )
        val contentSearches = parseSearchStrings(raw["content_searches"] ?: raw["contentSearches"])
            .ifEmpty { contentSearchesById[id].orEmpty() }

        // 视频真实文件由本地 HTTP 服务提供，这里根据 id 拼 URL。
        val videoUrl = videoUrlFor(id)

        // 多清晰度 URL，例如 720p/1080p。
        val qualityUrls = parseQualityUrls(raw)

        return VideoItem(
            id = id, title = title, author = author, avatarUrl = avatarUrl,
            videoUrl = videoUrl, coverUrl = coverUrl, coverThumbnailUrl = coverThumbnailUrl,
            likes = likes, comments = comments, collections = collections, shares = shares,
            tags = tags, recommendWords = recommendWords, contentSearches = contentSearches,
            qualityUrls = qualityUrls,
        )
    }

    /**
     * 把一条图片/图集原始数据转换成 CardItem。
     *
     * 判断规则：
     * - imageUrls 只有 1 张：返回 ImageCardItem。
     * - imageUrls 有多张：返回 AlbumCardItem。
     *
     * 所以图集不是很多个 ImageCardItem，
     * 而是一张 AlbumCardItem，内部有多个 AlbumSlide。
     */
    private fun parseImageCardItem(raw: Map<String, Any>): CardItem? {
        val id = raw.str("id") ?: raw.str("aweme_id") ?: return null
        val title = raw.str("title") ?: raw.str("desc") ?: return null
        val author = raw.str("author") ?: raw.str("nickname") ?: ""
        val avatarUrl = parseLocalOrRemoteUrl(
            raw.str("avatar_url") ?: raw.str("avatarUrl") ?: raw.str("avatar"),
        )
        val imageUrls = parseUrlList(
            raw["image_urls"] ?: raw["imageUrls"] ?: raw["note_download_url"],
        ).map(::parseLocalOrRemoteUrl)
            .filter { it.isNotBlank() }
            .distinct()
        if (imageUrls.isEmpty()) return null

        val likes = raw.int("liked_count").takeIf { it > 0 } ?: raw.int("likes")
        val comments = raw.int("comment_count").takeIf { it > 0 } ?: raw.int("comments")
        val shares = raw.int("share_count").takeIf { it > 0 } ?: raw.int("shares")
        val bgMusicUrl = parseLocalOrRemoteUrl(
            raw.str("bg_music_url") ?: raw.str("bgMusicUrl") ?: raw.str("music_download_url"),
        )
        val tags = parseTags(raw)
        val recommendWords = parseSearchStrings(raw["recommend_words"] ?: raw["recommendWords"])
            .ifEmpty {
                SearchSuggestionEngine.inferSearches(
                    title = title,
                    tags = tags,
                    count = 10,
                )
            }

        return if (imageUrls.size == 1) {
            ImageCardItem(
                id = id,
                title = title,
                author = author,
                imageUrl = imageUrls.first(),
                likes = likes,
                comments = comments,
                bgMusicUrl = bgMusicUrl,
            )
        } else {
            AlbumCardItem(
                id = id,
                title = title,
                author = author,
                avatarUrl = avatarUrl,
                slides = imageUrls.map { url ->
                    AlbumSlide(type = AlbumSlide.TYPE_IMAGE, mediaUrl = url)
                },
                bgMusicUrl = bgMusicUrl,
                likes = likes,
                comments = comments,
                shares = shares,
                tags = tags,
                recommendWords = recommendWords,
            )
        }
    }

    // ===== App 简化格式解析兜底 =====

    /**
     * 解析 App 自己定义的简化视频格式。
     *
     * 有些数据不是 MediaCrawler 原始字段名，而是已经整理成：
     * id/title/author/videoUrl/coverUrl 这种更接近 App 模型的字段。
     *
     * parseJsonArray 会在没有 aweme_id 时走到这里。
     */
    private fun parseAppFormatItem(
        raw: Map<String, Any>,
        contentSearchesById: Map<String, List<String>>,
    ): VideoItem? {
        val id = raw.str("id") ?: return null
        val title = raw.str("title") ?: return null
        val author = raw.str("author") ?: ""
        val avatarUrl = parseLocalOrRemoteUrl(raw.str("avatarUrl"))
        val videoUrl = raw.str("videoUrl") ?: ""
        val coverUrl = parseLocalOrRemoteUrl(raw.str("coverUrl"))
        val coverThumbnailUrl = parseLocalOrRemoteUrl(
            raw.str("coverThumbnailUrl") ?: raw.str("cover_thumbnail_url"),
        )
        val recommendWords = SearchSuggestionEngine.inferSearches(
            title = title,
            tags = emptyList(),
            count = 10,
        )
        val contentSearches = parseSearchStrings(raw["content_searches"] ?: raw["contentSearches"])
            .ifEmpty { contentSearchesById[id].orEmpty() }
        return VideoItem(id, title, author, avatarUrl, videoUrl, coverUrl,
            coverThumbnailUrl = coverThumbnailUrl,
            likes = raw.int("likes"), comments = raw.int("comments"),
            collections = raw.int("collections"), shares = raw.int("shares"),
            tags = emptyList(), recommendWords = recommendWords, contentSearches = contentSearches,
            qualityUrls = parseQualityUrls(raw),
        )
    }

    // ===== 工具方法 =====

    /**
     * 从 assets 中读取某个文件，返回完整文本。
     *
     * 文件不存在或读取失败时返回 null，调用方会尝试兜底文件名或返回空列表。
     */
    private fun readAssetFile(context: Context, filename: String): String? {
        return try {
            context.assets.open(filename).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        } catch (_: Exception) { null }
    }

    /**
     * 从 Map 中安全读取字符串字段。
     *
     * 空字符串会当作没有值处理。
     */
    private fun Map<String, Any>.str(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotEmpty() }

    /**
     * 从 Map 中安全读取 Int 字段。
     *
     * JSON 里数字可能被 Gson 解析成 Number，也可能原本就是字符串，
     * 所以这里兼容两种情况。
     */
    private fun Map<String, Any>.int(key: String): Int =
        when (val v = this[key]) {
            // 超出 Int 范围的计数（Gson 会解析为 Long/Double）直接 toInt() 会
            // 截断成负数或错误值；钳制到 [0, Int.MAX] 避免负数计数进入热度计算。
            is Number -> v.toDouble().toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            is String -> v.toIntOrNull()?.coerceAtLeast(0) ?: 0
            else -> 0
        }

    /**
     * 解析多清晰度 URL。
     *
     * 支持字段名：
     * - quality_urls
     * - qualityUrls
     *
     * 返回值形如：
     * "720p" -> "http://host:8081/videos/xxx/video_720p.mp4"
     */
    private fun parseQualityUrls(raw: Map<String, Any>): Map<String, String>? {
        val source = raw["quality_urls"] ?: raw["qualityUrls"] ?: return null
        if (source !is Map<*, *>) return null
        return source.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            val path = value as? String ?: return@mapNotNull null
            if (name.isBlank() || path.isBlank()) return@mapNotNull null
            name to parseLocalOrRemoteUrl(path)
        }.toMap().takeIf { it.isNotEmpty() }
    }

    /**
     * 解析搜索词列表。
     *
     * 支持两种格式：
     * - ["花束", "婚礼"]
     * - [{"keyword": "花束"}, {"keyword": "婚礼"}]
     */
    private fun parseSearchStrings(source: Any?): List<String> {
        val values = source as? List<*> ?: return emptyList()
        return values.mapNotNull { value ->
            when (value) {
                is String -> value
                is Map<*, *> -> value["keyword"] as? String
                else -> null
            }?.trim()?.takeIf { it.length >= 2 }
        }.distinct()
    }

    /**
     * 解析标签。
     *
     * 优先读取 tags 字段，再把 source_keyword 也作为一个标签补进去。
     */
    private fun parseTags(raw: Map<String, Any>): List<String> {
        val explicit = parseSearchStrings(raw["tags"])
        val keyword = raw.str("source_keyword")
        return (explicit + listOfNotNull(keyword)).distinct()
    }

    /**
     * 解析 URL 列表。
     *
     * 支持：
     * - JSON 数组
     * - 用逗号分隔的字符串
     */
    private fun parseUrlList(source: Any?): List<String> {
        return when (source) {
            is List<*> -> source.mapNotNull { it as? String }
            is String -> source.split(",")
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 把本地相对路径或远程 URL 统一转换成可访问 URL。
     *
     * - 已经是 http 开头：原样返回。
     * - 本地相对路径：补上 hostBaseUrl()。
     */
    private fun parseLocalOrRemoteUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.startsWith("http")) path else localAssetUrlFor(path)
    }
}
