package com.example.flower_show.model

/**
 * 视频清晰度选择工具。
 *
 * 它不保存状态，只提供一组纯函数：
 * - 把 VideoItem.qualityUrls 转成排序后的 VideoQuality 列表
 * - 根据模式选择当前清晰度
 * - 找到更低/更高一级清晰度
 * - 判断当前带宽是否足够播放某个清晰度
 *
 * 你可以先把这个文件当作“播放器清晰度逻辑的辅助类”，不需要第一遍完全掌握。
 */
object VideoQualitySelector {
    /**
     * 从清晰度名称里提取高度。
     *
     * 能匹配：
     * - "720p"
     * - "720"
     * - "1080P"
     *
     * Regex 里的 \d{3,4} 表示 3 到 4 位数字。
     */
    private val heightPattern = Regex("""(\d{3,4})\s*p?""", RegexOption.IGNORE_CASE)
    private val displayNamePattern = Regex("""^(\d{3,4})\s*p?$""", RegexOption.IGNORE_CASE)

    /**
     * 把原始的清晰度 Map 转成 VideoQuality 列表。
     *
     * 输入示例：
     * ```
     * mapOf(
     *     "1080p" to "https://.../video_1080.mp4",
     *     "720p" to "https://.../video_720.mp4"
     * )
     * ```
     *
     * 输出会做几件事：
     * - 去掉空名称或空 url
     * - 解析高度，例如 "720p" -> 720
     * - 根据高度估算码率
     * - 去重：同名清晰度只保留一个
     * - 排序：高清在前，低清在后
     */
    fun from(qualityUrls: Map<String, String>?): List<VideoQuality> {
        return qualityUrls.orEmpty()
            .mapNotNull { (rawName, rawUrl) ->
                // trim() 去掉前后空格，避免数据里有 " 720p " 这种情况。
                val name = rawName.trim()
                val url = rawUrl.trim()

                // 名称或地址为空时，这条清晰度无法播放，直接丢弃。
                if (name.isBlank() || url.isBlank()) return@mapNotNull null

                val height = parseHeight(name)
                VideoQuality(
                    name = name,
                    url = url,
                    height = height,
                    bitrateKbps = estimateBitrateKbps(height),
                )
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                // 首页播放器通常优先尝试较高清晰度，所以这里按高度从高到低排。
                compareByDescending<VideoQuality> { it.height }
                    .thenByDescending { it.bitrateKbps }
                    .thenBy { it.name },
            )
    }

    /**
     * 根据当前模式选择要播放的清晰度。
     *
     * 参数含义：
     * - qualities：可用清晰度列表，通常已经由 from() 排好序。
     * - mode：自动或手动。
     * - currentQualityName：当前正在使用的清晰度名称。
     *
     * Manual 会保留用户选择；Auto 首次使用不高于 480p 的最高档，
     * 先缩短起播时间，再由后续带宽判断决定是否升级。
     *
     * 真正的“自动降清晰度/升清晰度”逻辑在 lowerThan、higherThan、
     * hasBandwidthFor 以及 ViewModel/Player 侧组合完成。
     */
    fun chooseForMode(
        qualities: List<VideoQuality>,
        mode: QualityMode,
        currentQualityName: String?,
    ): VideoQuality? {
        if (qualities.isEmpty()) return null
        return when (mode) {
            QualityMode.Manual -> {
                qualities.find { it.name == currentQualityName } ?: qualities.first()
            }
            QualityMode.Auto -> {
                qualities
                    .filter { it.height in 1..AUTO_START_MAX_HEIGHT }
                    .maxByOrNull { it.height }
                    ?: qualities.last()
            }
        }
    }

    /**
     * 找到比当前清晰度低一级的选项。
     *
     * 因为 qualities 是高清在前、低清在后：
     * currentIndex + 1 就是更低一级。
     *
     * 如果当前已经是最低清晰度，返回 null。
     */
    fun lowerThan(qualities: List<VideoQuality>, currentQualityName: String?): VideoQuality? {
        val currentIndex = qualities.indexOfFirst { it.name == currentQualityName }
        if (currentIndex < 0 || currentIndex >= qualities.lastIndex) return null
        return qualities[currentIndex + 1]
    }

    /**
     * 找到比当前清晰度高一级的选项。
     *
     * 因为 qualities 是高清在前、低清在后：
     * currentIndex - 1 就是更高一级。
     *
     * 如果当前已经是最高清晰度，返回 null。
     */
    fun higherThan(qualities: List<VideoQuality>, currentQualityName: String?): VideoQuality? {
        val currentIndex = qualities.indexOfFirst { it.name == currentQualityName }
        if (currentIndex <= 0) return null
        return qualities[currentIndex - 1]
    }

    /**
     * 判断当前估算带宽是否足够播放某个清晰度。
     *
     * estimatedBandwidthKbps：播放器/网络层估算出来的当前带宽。
     * headroom：安全系数，例如 1.3 表示带宽至少要达到码率的 1.3 倍。
     *
     * 为什么要留安全系数？
     * 因为网络会波动，如果刚好等于视频码率，很容易卡顿。
     */
    fun hasBandwidthFor(quality: VideoQuality, estimatedBandwidthKbps: Int, headroom: Double): Boolean {
        // 如果码率或带宽未知，就不做拦截，避免因为缺少数据导致永远不能播放。
        if (quality.bitrateKbps <= 0 || estimatedBandwidthKbps <= 0) return true
        return estimatedBandwidthKbps >= (quality.bitrateKbps * headroom).toInt()
    }

    /**
     * 根据分辨率高度估算码率。
     *
     * 这是经验值，不是精确值。
     * 它的目的只是帮助自动清晰度逻辑做“够不够带宽”的粗略判断。
     */
    fun estimateBitrateKbps(height: Int): Int {
        return when {
            height >= 2160 -> 16_000
            height >= 1440 -> 8_000
            height >= 1080 -> 4_500
            height >= 720 -> 2_500
            height >= 480 -> 1_200
            height >= 360 -> 800
            height > 0 -> 600
            else -> 0
        }
    }

    fun qualityNameForVideoSize(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        return "${minOf(width, height)}p"
    }

    fun qualityNameForDisplay(name: String?): String? {
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val height = displayNamePattern.matchEntire(normalized)?.groupValues?.getOrNull(1)
        return if (height != null) "${height}P" else normalized
    }

    /**
     * 从清晰度名称里解析高度。
     *
     * 例如：
     * - "1080p" -> 1080
     * - "720P" -> 720
     * - "原画" -> 0
     */
    private fun parseHeight(name: String): Int {
        return heightPattern.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private const val AUTO_START_MAX_HEIGHT = 480
}
