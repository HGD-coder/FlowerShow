package com.example.flower_show.player

import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.example.flower_show.util.MetricsCollector
import com.example.flower_show.util.PerformanceDiagnostics

/**
 * Short-video oriented buffering profiles.
 *
 * Media3 defaults favor long-form playback with a 50s min/max buffer. Feed playback needs faster
 * first frame display and less bandwidth tied up in the current item, so the default profile keeps
 * a small playback gate and a bounded buffer window.
 */
object ShortVideoLoadControl {
    val DEFAULT_PROFILE: Profile = Profile.Balanced

    enum class Profile(val id: String) {
        FastStart("fast_start"),
        Balanced("balanced"),
        RebufferGuard("rebuffer_guard"),
    }

    data class Settings(
        val profile: Profile,
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
        val backBufferMs: Int,
        val retainBackBufferFromKeyframe: Boolean,
        val prioritizeTimeOverSizeThresholds: Boolean,
    )

    fun defaultSettings(): Settings = settingsFor(DEFAULT_PROFILE)

    fun settingsFor(profile: Profile): Settings = when (profile) {
        Profile.FastStart -> Settings(
            profile = profile,
            minBufferMs = 1_500,
            maxBufferMs = 6_000,
            bufferForPlaybackMs = 150,
            bufferForPlaybackAfterRebufferMs = 500,
            backBufferMs = 1_000,
            retainBackBufferFromKeyframe = true,
            prioritizeTimeOverSizeThresholds = true,
        )
        Profile.Balanced -> Settings(
            profile = profile,
            minBufferMs = 3_000,
            maxBufferMs = 10_000,
            bufferForPlaybackMs = 250,
            bufferForPlaybackAfterRebufferMs = 750,
            backBufferMs = 1_500,
            retainBackBufferFromKeyframe = true,
            prioritizeTimeOverSizeThresholds = true,
        )
        Profile.RebufferGuard -> Settings(
            profile = profile,
            minBufferMs = 5_000,
            maxBufferMs = 15_000,
            bufferForPlaybackMs = 500,
            bufferForPlaybackAfterRebufferMs = 1_200,
            backBufferMs = 2_500,
            retainBackBufferFromKeyframe = true,
            prioritizeTimeOverSizeThresholds = true,
        )
    }

    fun create(
        allocator: DefaultAllocator,
        settings: Settings = defaultSettings(),
    ): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                settings.minBufferMs,
                settings.maxBufferMs,
                settings.bufferForPlaybackMs,
                settings.bufferForPlaybackAfterRebufferMs,
            )
            .setBackBuffer(
                settings.backBufferMs,
                settings.retainBackBufferFromKeyframe,
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(settings.prioritizeTimeOverSizeThresholds)
            .build()
    }

    fun recordMetrics(settings: Settings) {
        MetricsCollector.recordLabel("load_control_profile", settings.profile.id)
        MetricsCollector.record("load_control_buffer_ms|type=min", settings.minBufferMs.toLong())
        MetricsCollector.record("load_control_buffer_ms|type=max", settings.maxBufferMs.toLong())
        MetricsCollector.record(
            "load_control_start_playback_ms|type=initial",
            settings.bufferForPlaybackMs.toLong(),
        )
        MetricsCollector.record(
            "load_control_start_playback_ms|type=after_rebuffer",
            settings.bufferForPlaybackAfterRebufferMs.toLong(),
        )
        MetricsCollector.record("load_control_back_buffer_ms", settings.backBufferMs.toLong())
        PerformanceDiagnostics.event(
            "load_control_profile",
            mapOf(
                "profile" to settings.profile.id,
                "minBufferMs" to settings.minBufferMs,
                "maxBufferMs" to settings.maxBufferMs,
                "startPlaybackMs" to settings.bufferForPlaybackMs,
                "afterRebufferMs" to settings.bufferForPlaybackAfterRebufferMs,
                "backBufferMs" to settings.backBufferMs,
                "prioritizeTime" to settings.prioritizeTimeOverSizeThresholds,
            ),
        )
    }
}
