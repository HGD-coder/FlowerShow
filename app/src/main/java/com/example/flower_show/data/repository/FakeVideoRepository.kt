package com.example.flower_show.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.flower_show.data.local.AssetJsonLoader
import com.example.flower_show.model.*
import com.example.flower_show.util.MetricsCollector

/**
 * FakeVideoRepository - Local data implementation (Singleton + Cache)
 *
 * Data priority: assets/video_data.json → hardcoded FallbackData.
 * Implements IVideoRepository for DIP compliance.
 */
class FakeVideoRepository private constructor(
    private val context: Context?,
    private val searchMatcher: SearchMatcher = WeightedContainsMatcher(),
) : IVideoRepository {

    private var cachedVideos: List<VideoItem>? = null

    companion object {
        @Volatile private var instance: FakeVideoRepository? = null

        fun getInstance(context: Context?): FakeVideoRepository {
            return instance ?: synchronized(this) {
                instance ?: FakeVideoRepository(context?.applicationContext).also { instance = it }
            }
        }

        @VisibleForTesting
        fun withMatcher(context: Context?, matcher: SearchMatcher): FakeVideoRepository {
            return FakeVideoRepository(context?.applicationContext, matcher)
        }
    }

    override fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>> {
        return try {
            val jsonVideos = getCachedVideos()
            val allItems = mutableListOf<CardItem>()
            if (jsonVideos.isNotEmpty()) allItems.addAll(jsonVideos)
            else allItems.addAll(FallbackData.createVideos())
            allItems.addAll(FallbackData.createImageCards())
            allItems.addAll(FallbackData.createAlbums())
            Result.success(paginate(allItems, page, pageSize))
        } catch (e: Exception) {
            Result.error("加载失败: ${e.message}")
        }
    }

    override fun search(keyword: String): Result<List<CardItem>> {
        return try {
            val videos = getCachedVideos().ifEmpty { FallbackData.createVideos() }
            val lower = keyword.lowercase().trim()
            if (lower.isEmpty()) return Result.success(emptyList())

            val startMs = System.currentTimeMillis()
            val strategyName = searchMatcher.javaClass.simpleName

            // Weighted scoring delegated to strategy
            val scored = videos.mapNotNull { v ->
                val s = searchMatcher.score(v, lower)
                if (s > 0f) v to s else null
            }
            val results = scored.sortedByDescending { it.second }.map { it.first }
            val timeMs = System.currentTimeMillis() - startMs

            MetricsCollector.record("search_query|strategy=${strategyName.lowercase()}", timeMs)
            MetricsCollector.record("search_result_count", results.size.toLong())
            Result.success(results)
        } catch (e: Exception) {
            Result.error("搜索失败: ${e.message}")
        }
    }

    override fun getRecommendWords(videoId: String): List<String> {
        val videos = getCachedVideos().ifEmpty { FallbackData.createVideos() }
        return videos.find { it.id == videoId }?.recommendWords ?: emptyList()
    }

    private fun getCachedVideos(): List<VideoItem> {
        if (cachedVideos == null) {
            cachedVideos = context?.let { AssetJsonLoader.loadVideos(it) } ?: emptyList()
        }
        return cachedVideos ?: emptyList()
    }

    private fun paginate(items: List<CardItem>, page: Int, pageSize: Int): List<CardItem> {
        val from = (page - 1) * pageSize
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + pageSize, items.size))
    }
}
