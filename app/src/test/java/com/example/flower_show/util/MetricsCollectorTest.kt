package com.example.flower_show.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCollectorTest {
    @Test
    fun summaryReturnsEmptyMessageWhenNoSamplesRecorded() {
        MetricsCollector.clear()

        val summary = MetricsCollector.summary()

        assertEquals("[MetricsCollector] No samples recorded.", summary)
    }

    @Test
    fun summaryReportsCacheHitRatioFromCacheBytes() {
        MetricsCollector.clear()

        MetricsCollector.record("cache_bytes|source=cache", 60)
        MetricsCollector.record("cache_bytes|source=upstream", 40)

        val summary = MetricsCollector.summary()

        assertTrue(summary.contains("Cache hit ratio: 60.0%"))
        assertTrue(summary.contains("60B cached / 100B total"))

        MetricsCollector.clear()
    }

    @Test
    fun summaryIncludesLabelsAndStatisticalValues() {
        MetricsCollector.clear()

        MetricsCollector.recordLabel("cache_ignored", "error")
        MetricsCollector.recordLabel("cache_ignored", "unset_length")
        MetricsCollector.record("video_first_frame", 100)
        MetricsCollector.record("video_first_frame", 200)
        MetricsCollector.record("video_first_frame", 300)

        val summary = MetricsCollector.summary()

        assertTrue(summary.contains("[cache_ignored]"))
        assertTrue(summary.contains("labels=error, unset_length"))
        assertTrue(summary.contains("[video_first_frame]"))
        assertTrue(summary.contains("samples=3"))
        assertTrue(summary.contains("avg=200ms"))
        assertTrue(summary.contains("p50=200ms"))
        assertTrue(summary.contains("p95=200ms"))
        assertTrue(summary.contains("min=100ms"))
        assertTrue(summary.contains("max=300ms"))

        MetricsCollector.clear()
    }

    @Test
    fun summaryReportsVideoStartupCacheImprovement() {
        MetricsCollector.clear()

        MetricsCollector.record("video_startup|cached=true", 100)
        MetricsCollector.record("video_startup|cached=false", 200)

        val summary = MetricsCollector.summary()

        assertTrue(summary.contains("Cache improvement"))
        assertTrue(summary.contains("saved 100ms"))

        MetricsCollector.clear()
    }

    @Test
    fun summaryReportsSearchLevenshteinOverhead() {
        MetricsCollector.clear()

        MetricsCollector.record("search_query|strategy=weighted", 10)
        MetricsCollector.record("search_query|strategy=levenshtein", 35)

        val summary = MetricsCollector.summary()

        assertTrue(summary.contains("Levenshtein overhead: +25ms per query"))

        MetricsCollector.clear()
    }

    @Test
    fun summaryFormatsLargeDurationsAndByteValues() {
        MetricsCollector.clear()

        MetricsCollector.record("video_startup", 1_500)
        MetricsCollector.record("thumbnail_bytes", 1_536)

        val summary = MetricsCollector.summary()

        assertTrue(summary.contains("1.5s"))
        assertTrue(summary.contains("1.5KB"))

        MetricsCollector.clear()
    }
}
