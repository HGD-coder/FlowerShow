package com.example.flower_show.util

import android.content.Intent

/**
 * Runtime switches used by local performance experiments.
 *
 * The test script passes [EXTRA_PROFILE] when launching the app, so the same APK can compare a
 * simulated pre-optimization baseline with the current optimized path.
 */
object PerformanceExperimentConfig {
    const val EXTRA_PROFILE = "perf_profile"

    data class Profile(
        val id: String,
        val enableCache: Boolean,
        val enablePreload: Boolean,
        val enableShortVideoLoadControl: Boolean,
    )

    private val baseline = Profile(
        id = "baseline",
        enableCache = false,
        enablePreload = false,
        enableShortVideoLoadControl = false,
    )

    private val optimized = Profile(
        id = "optimized",
        enableCache = true,
        enablePreload = true,
        enableShortVideoLoadControl = true,
    )

    private val cacheOnly = Profile(
        id = "cache_only",
        enableCache = true,
        enablePreload = false,
        enableShortVideoLoadControl = false,
    )

    private val preloadOnly = Profile(
        id = "preload_only",
        enableCache = false,
        enablePreload = true,
        enableShortVideoLoadControl = false,
    )

    private val loadControlOnly = Profile(
        id = "load_control_only",
        enableCache = false,
        enablePreload = false,
        enableShortVideoLoadControl = true,
    )

    @Volatile
    var current: Profile = optimized
        private set

    fun configureFromIntent(intent: Intent?) {
        val requested = intent?.getStringExtra(EXTRA_PROFILE).orEmpty()
        current = profileFor(requested)
        MetricsCollector.recordLabel("experiment_profile", current.id)
        PerformanceDiagnostics.event(
            "experiment_profile",
            mapOf(
                "profile" to current.id,
                "cache" to current.enableCache,
                "preload" to current.enablePreload,
                "shortLoadControl" to current.enableShortVideoLoadControl,
            ),
        )
    }

    fun profileFor(id: String): Profile {
        return when (id.trim().lowercase()) {
            baseline.id -> baseline
            cacheOnly.id -> cacheOnly
            preloadOnly.id -> preloadOnly
            loadControlOnly.id -> loadControlOnly
            optimized.id, "" -> optimized
            else -> optimized
        }
    }
}
