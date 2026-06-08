package com.example.flower_show.util

import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCollectorTest {
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
}
