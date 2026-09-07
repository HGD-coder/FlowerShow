package com.example.flower_show.ai

import kotlin.math.sqrt

object EmbeddingVectorUtils {
    fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) {
            sum += (value * value).toDouble()
        }
        val norm = sqrt(sum).toFloat()
        // NaN/Inf 向量不能参与归一化与余弦计算，直接原样返回由调用方兜底，
        // 避免把 NaN 扩散进整个向量索引。
        if (!norm.isFinite() || norm <= 0f) return vector
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        // 维度不一致说明查询向量与索引向量来自不同模型/配置，
        // 静默截断会产出错误排序；显式失败让搜索层捕获并给出错误结果，
        // 而不是把错得无声无息的分数合并进结果。
        require(a.size == b.size) {
            "Embedding dimension mismatch: ${a.size} vs ${b.size}"
        }
        if (a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (index in a.indices) {
            val av = a[index]
            val bv = b[index]
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        if (normA <= 0f || normB <= 0f || !dot.isFinite() || !normA.isFinite() || !normB.isFinite()) {
            return 0f
        }
        return dot / sqrt(normA * normB)
    }

    fun clampSimilarity(value: Float): Float {
        if (!value.isFinite()) return 0f
        return ((value + 1f) / 2f).coerceIn(0f, 1f)
    }
}
