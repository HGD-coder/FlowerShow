package com.example.flower_show.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoViewModelQualityPolicyTest {
    @Test
    fun adaptiveHlsStartsWith480pLabel() {
        assertEquals(
            "480p",
            initialPlaybackQualityName(
                useAdaptiveHls = true,
                selectedQualityName = "720p",
            ),
        )
    }

    @Test
    fun progressivePlaybackKeepsSelectedRawQualityName() {
        assertEquals(
            "720p",
            initialPlaybackQualityName(
                useAdaptiveHls = false,
                selectedQualityName = "720p",
            ),
        )
    }
}
