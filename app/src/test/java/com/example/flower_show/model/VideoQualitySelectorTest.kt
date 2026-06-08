package com.example.flower_show.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualitySelectorTest {
    @Test
    fun fromFiltersBlankUrlsAndSortsByHeightDescending() {
        val qualities = VideoQualitySelector.from(
            mapOf(
                "360p" to "https://cdn.example/360.mp4",
                "1080p" to "",
                "720p" to "https://cdn.example/720.mp4",
                "480p" to "https://cdn.example/480.mp4",
            ),
        )

        assertEquals(listOf("720p", "480p", "360p"), qualities.map { it.name })
        assertEquals(listOf(720, 480, 360), qualities.map { it.height })
    }

    @Test
    fun lowerAndHigherCandidatesStayInsideAvailableQualities() {
        val qualities = VideoQualitySelector.from(
            mapOf(
                "720p" to "720.mp4",
                "480p" to "480.mp4",
                "360p" to "360.mp4",
            ),
        )

        assertEquals("480p", VideoQualitySelector.lowerThan(qualities, "720p")?.name)
        assertEquals("720p", VideoQualitySelector.higherThan(qualities, "480p")?.name)
        assertNull(VideoQualitySelector.higherThan(qualities, "720p"))
        assertNull(VideoQualitySelector.lowerThan(qualities, "360p"))
    }

    @Test
    fun bandwidthHeadroomUsesEstimatedBitrateWhenKnown() {
        val quality = VideoQuality("720p", "720.mp4", height = 720, bitrateKbps = 2_500)

        assertTrue(VideoQualitySelector.hasBandwidthFor(quality, estimatedBandwidthKbps = 4_000, headroom = 1.35))
        assertFalse(VideoQualitySelector.hasBandwidthFor(quality, estimatedBandwidthKbps = 2_000, headroom = 1.35))
    }
}
