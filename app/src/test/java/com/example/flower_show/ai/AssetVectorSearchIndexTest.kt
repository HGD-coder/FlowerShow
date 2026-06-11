package com.example.flower_show.ai

import com.example.flower_show.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetVectorSearchIndexTest {
    @Test
    fun scoreReturnsOnlyRequestedVideoIds() {
        val index = indexOf(
            "match" to floatArrayOf(1f, 0f),
            "ignored" to floatArrayOf(0f, 1f),
        )

        val scores = index.score(
            queryEmbedding = floatArrayOf(1f, 0f),
            videos = listOf(video("match"), video("missing")),
        )

        assertEquals(setOf("match"), scores.keys)
        assertEquals(1f, scores.getValue("match"), 0.0001f)
    }

    @Test
    fun scoreClampsNegativeCosineToZero() {
        val index = indexOf("opposite" to floatArrayOf(-1f, 0f))

        val scores = index.score(
            queryEmbedding = floatArrayOf(1f, 0f),
            videos = listOf(video("opposite")),
        )

        assertEquals(0f, scores.getValue("opposite"), 0.0001f)
    }

    @Test
    fun scoreReturnsEmptyWhenIndexOrQueryIsEmpty() {
        assertTrue(indexOf().score(floatArrayOf(1f), listOf(video("a"))).isEmpty())
        assertTrue(indexOf("a" to floatArrayOf(1f)).score(floatArrayOf(), listOf(video("a"))).isEmpty())
    }

    @Test
    fun scoreIgnoresVideosWithoutStoredVectors() {
        val scores = indexOf("stored" to floatArrayOf(1f, 0f))
            .score(floatArrayOf(1f, 0f), listOf(video("other")))

        assertFalse(scores.containsKey("stored"))
        assertTrue(scores.isEmpty())
    }

    private fun indexOf(vararg vectors: Pair<String, FloatArray>): AssetVectorSearchIndex {
        val constructor = AssetVectorSearchIndex::class.java.getDeclaredConstructor(Map::class.java)
        constructor.isAccessible = true
        val normalized = vectors.associate { (id, vector) -> id to EmbeddingVectorUtils.normalize(vector) }
        return constructor.newInstance(normalized)
    }

    private fun video(id: String) = VideoItem(
        id = id,
        title = "title $id",
        author = "author",
        avatarUrl = "",
        videoUrl = "",
    )
}
