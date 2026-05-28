package com.example.flower_show.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * CacheManager — Global SimpleCache singleton for Media3 video caching.
 * 200MB LRU eviction, stored in app cache dir.
 */
object CacheManager {
    private const val MAX_CACHE_SIZE = 200L * 1024 * 1024  // 200 MB
    @Volatile private var cache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.cacheDir, "media_cache")
                SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE), StandaloneDatabaseProvider(context))
                    .also { cache = it }
            }
        }
    }

    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
        }
    }
}
