package com.example.flower_show.data.local

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.flower_show.model.VideoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * AssetJsonLoader - Read crawled video data / 读取爬虫数据
 *
 * Reads MediaCrawler JSONL from assets.
 * Video files served via local HTTP server (python -m http.server 8080).
 *
 * URL auto-detection:
 *   - Emulator → http://10.0.2.2:8080/videos/{aweme_id}/video.mp4
 *   - Real device → http://{LAN_IP}:8080/videos/{aweme_id}/video.mp4
 *
 * Change LAN_IP to your computer's actual local IP when using a real device.
 * Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
 */
object AssetJsonLoader {

    /** Your computer's LAN IP for real device connections.
     *  Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
     *  Phone and computer MUST be on the same WiFi network.
     *  Also ensure the HTTP server is running: `python -m http.server 8080` in the videos directory. */
    private const val LAN_IP = "10.138.179.51" // TODO: run `ipconfig` to verify and update

    private const val TAG = "AssetJsonLoader"
    private const val JSON_FILENAME = "video_data.json"
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
        return "http://$host:8080"
    }

    private fun videoUrlFor(awemeId: String): String =
        "${hostBaseUrl()}/videos/$awemeId/video.mp4"

    /** Build full URL from a relative path (e.g. "videos/123/video_480p.mp4") */
    private fun qualityUrlFor(relativePath: String): String =
        "${hostBaseUrl()}/$relativePath"

    fun loadVideos(context: Context): List<VideoItem> {
        return try {
            val content = readAssetFile(context, JSON_FILENAME)
                ?: readAssetFile(context, "video_data.jsonl")
                ?: return emptyList()

            val trimmed = content.trim()
            val videos = when {
                trimmed.startsWith("{") -> parseJsonl(trimmed)
                trimmed.startsWith("[") -> parseJsonArray(trimmed)
                else -> emptyList()
            }
            // Shuffle for mixed-category feed / 随机打乱，各类别混排
            videos.shuffled()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== JSONL parser / JSONL 解析 =====

    private fun parseJsonl(content: String): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(trimmed, Map::class.java) as Map<String, Any>
                parseMediaCrawlerItem(raw)?.let { videos.add(it) }
            } catch (_: Exception) { }
        }
        return videos
    }

    private fun parseJsonArray(content: String): List<VideoItem> {
        @Suppress("UNCHECKED_CAST")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(content, type)
        return rawList.mapNotNull { raw ->
            if (raw.containsKey("aweme_id")) parseMediaCrawlerItem(raw)
            else parseAppFormatItem(raw)
        }
    }

    // ===== MediaCrawler native format / 爬虫原生格式 =====

    @Suppress("UNCHECKED_CAST")
    private fun parseMediaCrawlerItem(raw: Map<String, Any>): VideoItem? {
        val id = raw.str("aweme_id") ?: return null
        val title = raw.str("title") ?: raw.str("desc") ?: return null
        val author = raw.str("nickname") ?: "未知作者"
        val avatarUrl = raw.str("avatar") ?: ""
        val coverUrl = raw.str("cover_url") ?: ""
        val likes = raw.int("liked_count")
        val comments = raw.int("comment_count")
        val collections = raw.int("collected_count")
        val shares = raw.int("share_count")
        val keyword = raw.str("source_keyword") ?: ""
        val tags = if (keyword.isNotEmpty()) listOf(keyword) else emptyList()
        val recommendWords = listOf(title.take(15))

        // Build video URL from local HTTP server (host auto-detected)
        val videoUrl = videoUrlFor(id)

        // Parse multi-quality URLs if present, prepend base URL
        val qualityUrls: Map<String, String>? = when (val qu = raw["quality_urls"]) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (qu as? Map<String, String>)?.mapValues { (_, path) ->
                    if (path.startsWith("http")) path else qualityUrlFor(path)
                }?.takeIf { it.isNotEmpty() }
            }
            else -> null
        }

        return VideoItem(
            id = id, title = title, author = author, avatarUrl = avatarUrl,
            videoUrl = videoUrl, coverUrl = coverUrl,
            likes = likes, comments = comments, collections = collections, shares = shares,
            tags = tags, recommendWords = recommendWords,
            qualityUrls = qualityUrls,
        )
    }

    // ===== App simplified format (fallback) / 简化格式兜底 =====

    private fun parseAppFormatItem(raw: Map<String, Any>): VideoItem? {
        val id = raw.str("id") ?: return null
        val title = raw.str("title") ?: return null
        val author = raw.str("author") ?: ""
        val avatarUrl = raw.str("avatarUrl") ?: ""
        val videoUrl = raw.str("videoUrl") ?: ""
        val coverUrl = raw.str("coverUrl") ?: ""
        return VideoItem(id, title, author, avatarUrl, videoUrl, coverUrl,
            likes = raw.int("likes"), comments = raw.int("comments"),
            collections = raw.int("collections"), shares = raw.int("shares"),
            tags = emptyList(), recommendWords = emptyList(),
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
}
