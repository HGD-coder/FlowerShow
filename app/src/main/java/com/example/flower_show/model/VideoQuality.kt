package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 单个视频清晰度选项。
 *
 * VideoItem 里保存的是 Map<String, String>：
 * 例如 "720p" -> "某个 720p 视频地址"。
 *
 * VideoQualitySelector 会把这个 Map 转换成 VideoQuality 列表，
 * 这样播放层就不只知道“名字和地址”，还知道分辨率高度、估算码率等信息。
 */
@Immutable
data class VideoQuality(
    /**
     * 展示名称，例如 "1080p"、"720p"。
     */
    val name: String,

    /**
     * 这个清晰度对应的视频地址。
     */
    val url: String,

    /**
     * 垂直分辨率高度，例如 1080、720、480。
     *
     * 如果解析不出来，就用 0 表示未知。
     */
    val height: Int = 0,

    /**
     * 估算码率，单位 kbps。
     *
     * 它不是从真实视频文件里读取的精确值，而是根据分辨率大致估算。
     * 自动清晰度切换时，会用它和当前带宽做比较。
     */
    val bitrateKbps: Int = 0,
)

/**
 * 清晰度选择模式。
 *
 * Auto：系统根据带宽、缓冲等情况选择清晰度。
 * Manual：用户手动选择某个清晰度，系统应尽量尊重用户选择。
 */
enum class QualityMode {
    /**
     * 自动清晰度模式。
     */
    Auto,

    /**
     * 手动清晰度模式。
     */
    Manual,
}
