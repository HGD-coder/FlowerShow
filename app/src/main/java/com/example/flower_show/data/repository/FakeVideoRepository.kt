package com.example.flower_show.data.repository

import android.content.Context
import com.example.flower_show.data.local.AssetJsonLoader
import com.example.flower_show.model.*

/**
 * FakeVideoRepository - Local data implementation (Singleton + Cache)
 *
 * Data priority: assets/video_data.json → hardcoded FallbackData.
 * Implements IVideoRepository for DIP compliance.
 */
class FakeVideoRepository private constructor(private val context: Context?) : IVideoRepository {

    private var cachedVideos: List<VideoItem>? = null

    companion object {
        @Volatile private var instance: FakeVideoRepository? = null

        fun getInstance(context: Context?): FakeVideoRepository {
            return instance ?: synchronized(this) {
                instance ?: FakeVideoRepository(context?.applicationContext).also { instance = it }
            }
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

            // Weighted scoring / 加权评分
            // Title exact match: 1.0, partial: 0.7
            // Tags match: 0.5 per tag
            // Recommend words match: 0.3 per word
            // Minimum threshold: 0.3
            val scored = videos.mapNotNull { v ->
                var score = 0f
                val titleLower = v.title.lowercase()
                if (titleLower == lower) score += 1.0f
                else if (titleLower.contains(lower)) score += 0.7f
                score += v.tags.count { it.lowercase().contains(lower) } * 0.5f
                score += v.recommendWords.count { it.lowercase().contains(lower) } * 0.3f
                if (score >= 0.3f) v to score else null
            }
            Result.success(scored.sortedByDescending { it.second }.map { it.first })
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
