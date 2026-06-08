package com.example.flower_show.util

import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stable trace section names consumed by the Macrobenchmark module.
 */
object PerformanceTrace {
    const val VIDEO_FIRST_FRAME = "FlowerVideoFirstFrame"
    const val VIDEO_BUFFERING = "FlowerVideoBuffering"
    const val QUALITY_SWITCH = "FlowerQualitySwitch"

    private val nextCookie = AtomicInteger(1)

    fun enableAppTracing() {
        Trace.forceEnableAppTracing()
    }

    fun beginAsyncSection(sectionName: String): Int {
        val cookie = nextCookie.getAndIncrement()
        Trace.beginAsyncSection(sectionName, cookie)
        return cookie
    }

    fun endAsyncSection(sectionName: String, cookie: Int?) {
        if (cookie != null) {
            Trace.endAsyncSection(sectionName, cookie)
        }
    }
}
