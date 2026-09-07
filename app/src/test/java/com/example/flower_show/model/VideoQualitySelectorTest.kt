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

    @Test
    fun autoModeStartsAt480pInsteadOfHighestQuality() {
        val qualities = VideoQualitySelector.from(
            mapOf(
                "1080p" to "1080.mp4",
                "720p" to "720.mp4",
                "480p" to "480.mp4",
                "360p" to "360.mp4",
            ),
        )

        val selected = VideoQualitySelector.chooseForMode(
            qualities = qualities,
            mode = QualityMode.Auto,
            currentQualityName = "1080p",
        )

        assertEquals("480p", selected?.name)
    }

    @Test
    fun videoSizeQualityNameUsesShortEdgeForLandscapeAndPortrait() {
        assertEquals("480p", VideoQualitySelector.qualityNameForVideoSize(width = 854, height = 480))
        assertEquals("480p", VideoQualitySelector.qualityNameForVideoSize(width = 480, height = 854))
        assertEquals("1080p", VideoQualitySelector.qualityNameForVideoSize(width = 1920, height = 1080))
        assertNull(VideoQualitySelector.qualityNameForVideoSize(width = 0, height = 1080))
    }

    @Test
    fun qualityDisplayNameUsesUppercasePSuffixWithoutChangingOtherNames() {
        assertEquals("480P", VideoQualitySelector.qualityNameForDisplay("480p"))
        assertEquals("720P", VideoQualitySelector.qualityNameForDisplay(" 720P "))
        assertEquals("1080P", VideoQualitySelector.qualityNameForDisplay("1080"))
        assertEquals("原画", VideoQualitySelector.qualityNameForDisplay("原画"))
        assertNull(VideoQualitySelector.qualityNameForDisplay(" "))
    }
}
