package com.example.flower_show.ai

import kotlin.math.sqrt

object EmbeddingVectorUtils {
    fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) {
            sum += (value * value).toDouble()
        }
        val norm = sqrt(sum).toFloat()
        if (norm <= 0f) return vector
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val size = minOf(a.size, b.size)
        if (size == 0) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (index in 0 until size) {
            val av = a[index]
            val bv = b[index]
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return dot / sqrt(normA * normB)
    }

    fun clampSimilarity(value: Float): Float {
        return ((value + 1f) / 2f).coerceIn(0f, 1f)
    }
}
