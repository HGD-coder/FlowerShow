package com.example.flower_show.model

import androidx.compose.runtime.Stable

/**
 * VideoItem - Data model for a video card / 视频卡片数据模型
 *
 * VideoItem - Data model for a video card / 视频卡片数据模型
 *
 * All fields are val (immutable). Kotlin data class auto-generates:
 * equals(), hashCode(), toString(), copy(), componentN().
 * Java 144 lines → Kotlin ~25 lines.
 *
 * Fields aligned with MediaCrawler Douyin output for backend migration.
 * 字段与 MediaCrawler 爬虫输出对齐，方便后续对接后端。
 */
data class VideoItem(
    // Core fields / 核心字段
    val id: String,                 // aweme_id
    val title: String,              // title/desc
    val author: String,             // nickname
    val avatarUrl: String,          // avatar

    // Media URLs / 媒体地址
    val videoUrl: String,           // video_download_url
    val coverUrl: String = "",          // cover_url
    val musicUrl: String? = null,   // music_download_url

    // Interaction stats / 互动数据
    val likes: Int = 0,             // liked_count
    val comments: Int = 0,          // comment_count
    val collections: Int = 0,       // collected_count
    val shares: Int = 0,            // share_count

    // Tags & recommendations / 标签和推荐词
    val tags: List<String> = emptyList(),
    val recommendWords: List<String> = emptyList(),

    // Backend-ready fields / 后端对接字段
    val userId: String? = null,         // user_id
    val creatorSecUid: String? = null,  // sec_uid
    val location: String? = null,       // ip_location
    val sourceUrl: String? = null,      // aweme_url
    val publishTime: Long = 0,          // create_time

    // Multi-quality / 多清晰度预留
    val qualityUrls: Map<String, String>? = null,
) : CardItem {
    override val itemType: CardItem = CardItem.TypeVideo
}
