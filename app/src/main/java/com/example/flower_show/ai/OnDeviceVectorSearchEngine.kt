package com.example.flower_show.ai

import android.content.Context
import android.util.Log
import com.example.flower_show.model.VideoItem
import com.example.flower_show.util.MetricsCollector

class OnDeviceVectorSearchEngine(
    context: Context,
) : VectorSearchEngine {
    private val appContext = context.applicationContext
    private val embeddingService: OnDeviceEmbeddingService? by lazy {
        OnDeviceEmbeddingService.createOrNull(appContext)
    }
    private val searchIndex: AssetVectorSearchIndex? by lazy {
        AssetVectorSearchIndex.fromAssetsOrNull(appContext)
    }

    override fun score(query: String, videos: List<VideoItem>): Map<String, Float> {
        val service = embeddingService ?: return emptyMap()
        val index = searchIndex ?: return emptyMap()
        return try {
            val startMs = System.currentTimeMillis()
            val embedding = service.embed(query) ?: return emptyMap()
            val scores = index.score(embedding, videos)
            MetricsCollector.record("vector_search_query", System.currentTimeMillis() - startMs)
            MetricsCollector.record("vector_search_result_count", scores.size.toLong())
            scores
        } catch (e: Exception) {
            Log.w(TAG, "Vector search failed; falling back to lexical search.", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "VectorSearchEngine"
    }
}
