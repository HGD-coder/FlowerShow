package com.example.flower_show.ai

import com.example.flower_show.model.VideoItem

/**
 * Optional semantic search engine.
 *
 * Implementations return scores in [0, 1]. Empty scores mean vector search is
 * unavailable and callers should fall back to lexical matching.
 */
fun interface VectorSearchEngine {
    fun score(query: String, videos: List<VideoItem>): Map<String, Float>
}
