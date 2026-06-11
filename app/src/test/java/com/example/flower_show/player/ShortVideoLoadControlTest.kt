package com.example.flower_show.player

import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.example.flower_show.util.MetricsCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun createBuildsLoadControlForEveryProfile() {
        ShortVideoLoadControl.Profile.entries.forEach { profile ->
            val loadControl = ShortVideoLoadControl.create(
                allocator = DefaultAllocator(true, 64 * 1024),
                settings = ShortVideoLoadControl.settingsFor(profile),
            )

            assertNotNull(loadControl)
        }
    }

    @Test
    fun recordMetricsWritesProfileAndBufferSettings() {
        MetricsCollector.clear()
        val settings = ShortVideoLoadControl.settingsFor(ShortVideoLoadControl.Profile.FastStart)

        ShortVideoLoadControl.recordMetrics(settings)

        val summary = MetricsCollector.summary()
        assertTrue(summary.contains("load_control_profile"))
        assertTrue(summary.contains("fast_start"))
        assertTrue(summary.contains("load_control_buffer_ms|type=min"))
        assertTrue(summary.contains("load_control_start_playback_ms|type=initial"))
        assertTrue(summary.contains("load_control_back_buffer_ms"))

        MetricsCollector.clear()
    }
}
