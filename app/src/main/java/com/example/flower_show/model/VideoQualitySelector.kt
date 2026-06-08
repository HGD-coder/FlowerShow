package com.example.flower_show.model

object VideoQualitySelector {
    private val heightPattern = Regex("""(\d{3,4})\s*p?""", RegexOption.IGNORE_CASE)

    fun from(qualityUrls: Map<String, String>?): List<VideoQuality> {
        return qualityUrls.orEmpty()
            .mapNotNull { (rawName, rawUrl) ->
                val name = rawName.trim()
                val url = rawUrl.trim()
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
                compareByDescending<VideoQuality> { it.height }
                    .thenByDescending { it.bitrateKbps }
                    .thenBy { it.name },
            )
    }

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
                qualities.find { it.name == currentQualityName } ?: qualities.first()
            }
        }
    }

    fun lowerThan(qualities: List<VideoQuality>, currentQualityName: String?): VideoQuality? {
        val currentIndex = qualities.indexOfFirst { it.name == currentQualityName }
        if (currentIndex < 0 || currentIndex >= qualities.lastIndex) return null
        return qualities[currentIndex + 1]
    }

    fun higherThan(qualities: List<VideoQuality>, currentQualityName: String?): VideoQuality? {
        val currentIndex = qualities.indexOfFirst { it.name == currentQualityName }
        if (currentIndex <= 0) return null
        return qualities[currentIndex - 1]
    }

    fun hasBandwidthFor(quality: VideoQuality, estimatedBandwidthKbps: Int, headroom: Double): Boolean {
        if (quality.bitrateKbps <= 0 || estimatedBandwidthKbps <= 0) return true
        return estimatedBandwidthKbps >= (quality.bitrateKbps * headroom).toInt()
    }

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

    private fun parseHeight(name: String): Int {
        return heightPattern.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
