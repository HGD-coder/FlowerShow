package com.example.flower_show.ai

import android.content.Context
import android.util.Log
import com.example.flower_show.model.VideoItem
import com.google.gson.Gson
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AssetVectorSearchIndex private constructor(
    private val vectorsByVideoId: Map<String, FloatArray>,
) {
    fun score(
        queryEmbedding: FloatArray,
        videos: List<VideoItem>,
    ): Map<String, Float> {
        if (vectorsByVideoId.isEmpty() || queryEmbedding.isEmpty()) return emptyMap()
        val candidateIds = videos.mapTo(mutableSetOf()) { it.id }
        return buildMap {
            vectorsByVideoId.forEach { (id, vector) ->
                if (id in candidateIds) {
                    val score = EmbeddingVectorUtils.cosine(queryEmbedding, vector).coerceIn(0f, 1f)
                    put(id, score)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VectorSearchIndex"
        private const val DEFAULT_MANIFEST_ASSET = "search/video_embedding_manifest.json"
        private const val DEFAULT_VECTORS_ASSET = "search/video_embeddings.bin"

        fun fromAssetsOrNull(
            context: Context,
            manifestAssetPath: String = DEFAULT_MANIFEST_ASSET,
            vectorsAssetPath: String = DEFAULT_VECTORS_ASSET,
        ): AssetVectorSearchIndex? {
            return try {
                val manifestJson = context.assets.open(manifestAssetPath)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val manifest = Gson().fromJson(manifestJson, EmbeddingManifest::class.java)
                if (manifest.dimension <= 0 || manifest.items.isEmpty()) return null

                val bytes = context.assets.open(vectorsAssetPath).use { it.readBytes() }
                val floats = ByteBuffer.wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                val vectorsByVideoId = linkedMapOf<String, FloatArray>()
                manifest.items.forEachIndexed { index, item ->
                    val offset = item.offset.takeIf { it >= 0 } ?: index
                    val start = offset * manifest.dimension
                    if (item.id.isBlank() || start < 0 || start + manifest.dimension > floats.limit()) {
                        return@forEachIndexed
                    }
                    val vector = FloatArray(manifest.dimension)
                    floats.position(start)
                    floats.get(vector)
                    vectorsByVideoId[item.id] = EmbeddingVectorUtils.normalize(vector)
                }
                AssetVectorSearchIndex(vectorsByVideoId.takeIf { it.isNotEmpty() } ?: return null)
            } catch (e: Exception) {
                Log.i(TAG, "Vector index assets are not available yet.")
                null
            }
        }
    }

    private data class EmbeddingManifest(
        val version: Int = 1,
        val model: String = "",
        val dimension: Int = 0,
        val items: List<EmbeddingItem> = emptyList(),
    )

    private data class EmbeddingItem(
        val id: String = "",
        val offset: Int = -1,
    )
}
