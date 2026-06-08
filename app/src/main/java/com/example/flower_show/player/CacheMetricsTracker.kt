package com.example.flower_show.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import com.example.flower_show.util.MetricsCollector
import java.util.concurrent.atomic.AtomicLong

object CacheMetricsTracker {
    private val cachedBytes = AtomicLong(0)
    private val upstreamBytes = AtomicLong(0)
    private val cacheIgnoredCount = AtomicLong(0)

    val cacheEventListener = object : CacheDataSource.EventListener {
        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            if (cachedBytesRead <= 0) return
            cachedBytes.addAndGet(cachedBytesRead)
            MetricsCollector.record("cache_bytes|source=cache", cachedBytesRead)
            MetricsCollector.record("cache_size_bytes", cacheSizeBytes)
        }

        override fun onCacheIgnored(reason: Int) {
            cacheIgnoredCount.incrementAndGet()
            MetricsCollector.recordLabel("cache_ignored", cacheIgnoredReasonLabel(reason))
        }
    }

    val upstreamTransferListener = object : TransferListener {
        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) = Unit

        override fun onTransferStart(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            if (!isNetwork || bytesTransferred <= 0) return
            val bytes = bytesTransferred.toLong()
            upstreamBytes.addAndGet(bytes)
            MetricsCollector.record("cache_bytes|source=upstream", bytes)
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) = Unit
    }

    fun snapshot(): CacheMetricsSnapshot {
        val cached = cachedBytes.get()
        val upstream = upstreamBytes.get()
        return CacheMetricsSnapshot(
            cachedBytes = cached,
            upstreamBytes = upstream,
            cacheIgnoredCount = cacheIgnoredCount.get(),
        )
    }

    fun clear() {
        cachedBytes.set(0)
        upstreamBytes.set(0)
        cacheIgnoredCount.set(0)
    }

    private fun cacheIgnoredReasonLabel(reason: Int): String {
        return when (reason) {
            CacheDataSource.CACHE_IGNORED_REASON_ERROR -> "error"
            CacheDataSource.CACHE_IGNORED_REASON_UNSET_LENGTH -> "unset_length"
            else -> "unknown_$reason"
        }
    }
}

data class CacheMetricsSnapshot(
    val cachedBytes: Long,
    val upstreamBytes: Long,
    val cacheIgnoredCount: Long,
) {
    val totalReadBytes: Long
        get() = cachedBytes + upstreamBytes

    val hitRatio: Double
        get() = if (totalReadBytes == 0L) 0.0 else cachedBytes.toDouble() / totalReadBytes
}
