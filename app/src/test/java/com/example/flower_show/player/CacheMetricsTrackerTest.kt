package com.example.flower_show.player

import androidx.media3.datasource.cache.CacheDataSource
import com.example.flower_show.util.MetricsCollector
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheMetricsTrackerTest {
    @Test
    fun snapshotStartsEmptyAfterClear() {
        CacheMetricsTracker.clear()

        val snapshot = CacheMetricsTracker.snapshot()

        assertEquals(0L, snapshot.cachedBytes)
        assertEquals(0L, snapshot.upstreamBytes)
        assertEquals(0L, snapshot.cacheIgnoredCount)
        assertEquals(0L, snapshot.totalReadBytes)
        assertEquals(0.0, snapshot.hitRatio, 0.0001)
    }

    @Test
    fun cacheEventListenerAccumulatesPositiveCachedBytesOnly() {
        CacheMetricsTracker.clear()
        MetricsCollector.clear()

        CacheMetricsTracker.cacheEventListener.onCachedBytesRead(1_024, 256)
        CacheMetricsTracker.cacheEventListener.onCachedBytesRead(1_024, 0)

        val snapshot = CacheMetricsTracker.snapshot()

        assertEquals(256L, snapshot.cachedBytes)
        assertEquals(256L, snapshot.totalReadBytes)
        assertEquals(1.0, snapshot.hitRatio, 0.0001)

        CacheMetricsTracker.clear()
        MetricsCollector.clear()
    }

    @Test
    fun cacheIgnoredEventsAreCounted() {
        CacheMetricsTracker.clear()
        MetricsCollector.clear()

        CacheMetricsTracker.cacheEventListener.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_ERROR)
        CacheMetricsTracker.cacheEventListener.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_UNSET_LENGTH)
        CacheMetricsTracker.cacheEventListener.onCacheIgnored(999)

        assertEquals(3L, CacheMetricsTracker.snapshot().cacheIgnoredCount)

        CacheMetricsTracker.clear()
        MetricsCollector.clear()
    }

    @Test
    fun cacheMetricsSnapshotCalculatesHitRatioFromCacheAndUpstreamBytes() {
        val snapshot = CacheMetricsSnapshot(
            cachedBytes = 65,
            upstreamBytes = 35,
            cacheIgnoredCount = 2,
        )

        assertEquals(100L, snapshot.totalReadBytes)
        assertEquals(0.65, snapshot.hitRatio, 0.0001)
        assertEquals(2L, snapshot.cacheIgnoredCount)
    }
}
