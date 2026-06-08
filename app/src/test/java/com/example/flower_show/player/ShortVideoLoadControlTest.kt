package com.example.flower_show.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoLoadControlTest {
    @Test
    fun defaultProfileIsBalancedForFeedPlayback() {
        val settings = ShortVideoLoadControl.defaultSettings()

        assertEquals(ShortVideoLoadControl.Profile.Balanced, settings.profile)
        assertEquals(250, settings.bufferForPlaybackMs)
        assertEquals(10_000, settings.maxBufferMs)
    }

    @Test
    fun profilesKeepValidLoadControlThresholds() {
        ShortVideoLoadControl.Profile.entries.forEach { profile ->
            val settings = ShortVideoLoadControl.settingsFor(profile)

            assertTrue(settings.maxBufferMs >= settings.minBufferMs)
            assertTrue(settings.minBufferMs >= settings.bufferForPlaybackAfterRebufferMs)
            assertTrue(settings.bufferForPlaybackAfterRebufferMs >= settings.bufferForPlaybackMs)
            assertTrue(settings.backBufferMs >= 0)
        }
    }

    @Test
    fun profilesStayInsideShortVideoBufferBudget() {
        val settings = ShortVideoLoadControl.Profile.entries.map(ShortVideoLoadControl::settingsFor)

        assertTrue(settings.all { it.maxBufferMs <= 15_000 })
        assertTrue(settings.all { it.bufferForPlaybackMs <= 500 })
    }
}
