package com.example.flower_show.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * CacheManager - app-wide Media3 cache owner.
 *
 * SimpleCache must stay a singleton for its directory. Playback and feed preloading both request
 * CacheDataSource.Factory from here so they share disk data and cache metrics.
 */
object CacheManager {
    private const val CACHE_DIR_NAME = "media_cache"
    private const val MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB

    @Volatile private var cache: SimpleCache? = null
    @Volatile private var databaseProvider: StandaloneDatabaseProvider? = null

    fun getInstance(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val appContext = context.applicationContext
                SimpleCache(
                    getCacheDirectory(appContext),
                    LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
                    getDatabaseProvider(appContext),
                ).also { cache = it }
            }
        }
    }

    fun createCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setTransferListener(CacheMetricsTracker.upstreamTransferListener)

        return CacheDataSource.Factory()
            .setCache(getInstance(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(CacheMetricsTracker.cacheEventListener)
    }

    fun getCacheDirectory(context: Context): File {
        return File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
    }

    fun snapshot(context: Context): CacheSnapshot {
        val appContext = context.applicationContext
        val simpleCache = getInstance(appContext)
        return CacheSnapshot(
            directory = getCacheDirectory(appContext).absolutePath,
            maxSizeBytes = MAX_CACHE_SIZE_BYTES,
            usedBytes = simpleCache.cacheSpace,
            byteMetrics = CacheMetricsTracker.snapshot(),
        )
    }

    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
            databaseProvider = null
        }
    }

    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        return databaseProvider ?: synchronized(this) {
            databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext)
                .also { databaseProvider = it }
        }
    }
}

data class CacheSnapshot(
    val directory: String,
    val maxSizeBytes: Long,
    val usedBytes: Long,
    val byteMetrics: CacheMetricsSnapshot,
) {
    val usageRatio: Double
        get() = if (maxSizeBytes == 0L) 0.0 else usedBytes.toDouble() / maxSizeBytes
}
