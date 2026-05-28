package com.example.flower_show.model

/**
 * VideoQuality — A single quality option for video playback.
 * 单个视频清晰度选项
 *
 * @param name Display name e.g. "720p"
 * @param url Video URL for this quality
 * @param height Vertical resolution in pixels
 * @param bitrateKbps Estimated bitrate in kbps
 */
data class VideoQuality(
    val name: String,
    val url: String,
    val height: Int = 0,
    val bitrateKbps: Int = 0,
)

/**
 * QualityMode — Who decides the current quality.
 * Auto: System switches based on buffering/bandwidth
 * Manual: User picked a specific quality
 */
enum class QualityMode {
    Auto,
    Manual,
}
