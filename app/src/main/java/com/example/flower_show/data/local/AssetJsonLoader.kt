package com.example.flower_show.data.local

import android.content.Context
import android.os.Build
import android.util.Log
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
 * AssetJsonLoader - Read crawled video data / 读取爬虫数据
 *
 * Reads MediaCrawler JSONL from assets.
 * Video files served via local range-aware HTTP server on port 8081.
 *
 * URL auto-detection:
 *   - Emulator → http://10.0.2.2:8081/videos/{aweme_id}/video.mp4
 *   - Real device → http://{LAN_IP}:8081/videos/{aweme_id}/video.mp4
 *
 * Change LAN_IP to your computer's actual local IP when using a real device.
 * Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
 */
object AssetJsonLoader {

    /** Your computer's LAN IP for real device connections.
     *  Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
     *  Phone and computer MUST be on the same WiFi network.
     *  Also ensure the HTTP server is running in the douyin directory:
     *    cd D:\MediaCrawler\MediaCrawler\data\douyin
     *    uv run python tools\video_http_server.py --directory data\douyin --port 8081
     *  Or just double-click start_video_server.bat in MediaCrawler/. */
    private const val LAN_IP = "10.138.179.51" // TODO: run `ipconfig` to verify and update

    private const val TAG = "AssetJsonLoader"
    private const val JSON_FILENAME = "video_data.json"
    private const val IMAGE_JSON_FILENAME = "image_data.json"
    private val gson = Gson()

    /** Auto-detect emulator vs real device and pick the right host address. */
    private fun hostBaseUrl(): String {
        val isEmulator = Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
        val host = if (isEmulator) "10.0.2.2" else LAN_IP
        val deviceType = if (isEmulator) "emulator" else "real device"
        Log.d(TAG, "Detected $deviceType, using host: $host")
        return "http://$host:8081"
    }

    private fun videoUrlFor(awemeId: String): String =
        "${hostBaseUrl()}/videos/$awemeId/video.mp4"

    /** Build full URL from a relative path (e.g. "videos/123/video_480p.mp4") */
    private fun localAssetUrlFor(relativePath: String): String =
        "${hostBaseUrl()}/$relativePath"

    fun loadVideos(context: Context): List<VideoItem> {
        return try {
            val content = readAssetFile(context, JSON_FILENAME)
                ?: readAssetFile(context, "video_data.jsonl")
                ?: return emptyList()

            val contentSearches = AssetSearchSuggestionLoader.loadVideoContentSearches(context)
            val trimmed = content.trim()
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

    fun loadImageCards(context: Context): List<CardItem> {
        return try {
            val content = readAssetFile(context, IMAGE_JSON_FILENAME)
                ?: readAssetFile(context, "image_data.jsonl")
                ?: return emptyList()

            val trimmed = content.trim()
            when {
                trimmed.startsWith("[") -> parseImageJsonArray(trimmed)
                trimmed.startsWith("{") -> parseImageJsonl(trimmed)
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== JSONL parser / JSONL 解析 =====

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

    private fun parseImageJsonArray(content: String): List<CardItem> {
        @Suppress("UNCHECKED_CAST")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(content, type)
        return rawList.mapNotNull(::parseImageCardItem)
    }

    // ===== MediaCrawler native format / 爬虫原生格式 =====

    @Suppress("UNCHECKED_CAST")
    private fun parseMediaCrawlerItem(
        raw: Map<String, Any>,
        contentSearchesById: Map<String, List<String>>,
    ): VideoItem? {
        val id = raw.str("aweme_id") ?: return null
        val title = raw.str("title") ?: raw.str("desc") ?: return null
        val author = raw.str("nickname") ?: "未知作者"
        val avatarUrl = raw.str("avatar") ?: ""
        val coverUrl = raw.str("cover_url") ?: ""
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

        // Build video URL from local HTTP server (host auto-detected)
        val videoUrl = videoUrlFor(id)

        val qualityUrls = parseQualityUrls(raw)

        return VideoItem(
            id = id, title = title, author = author, avatarUrl = avatarUrl,
            videoUrl = videoUrl, coverUrl = coverUrl, coverThumbnailUrl = coverThumbnailUrl,
            likes = likes, comments = comments, collections = collections, shares = shares,
            tags = tags, recommendWords = recommendWords, contentSearches = contentSearches,
            qualityUrls = qualityUrls,
        )
    }

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

    // ===== App simplified format (fallback) / 简化格式兜底 =====

    private fun parseAppFormatItem(
        raw: Map<String, Any>,
        contentSearchesById: Map<String, List<String>>,
    ): VideoItem? {
        val id = raw.str("id") ?: return null
        val title = raw.str("title") ?: return null
        val author = raw.str("author") ?: ""
        val avatarUrl = raw.str("avatarUrl") ?: ""
        val videoUrl = raw.str("videoUrl") ?: ""
        val coverUrl = raw.str("coverUrl") ?: ""
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

    // ===== Utilities / 工具方法 =====

    private fun readAssetFile(context: Context, filename: String): String? {
        return try {
            context.assets.open(filename).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        } catch (_: Exception) { null }
    }

    private fun Map<String, Any>.str(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotEmpty() }

    private fun Map<String, Any>.int(key: String): Int =
        when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }

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

    private fun parseTags(raw: Map<String, Any>): List<String> {
        val explicit = parseSearchStrings(raw["tags"])
        val keyword = raw.str("source_keyword")
        return (explicit + listOfNotNull(keyword)).distinct()
    }

    private fun parseUrlList(source: Any?): List<String> {
        return when (source) {
            is List<*> -> source.mapNotNull { it as? String }
            is String -> source.split(",")
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseLocalOrRemoteUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.startsWith("http")) path else localAssetUrlFor(path)
    }
}
