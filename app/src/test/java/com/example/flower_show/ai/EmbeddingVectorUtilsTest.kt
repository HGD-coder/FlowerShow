package com.example.flower_show.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EmbeddingVectorUtilsTest {
    @Test
    fun normalizeScalesVectorToUnitLength() {
        val normalized = EmbeddingVectorUtils.normalize(floatArrayOf(3f, 4f))

        assertArrayEquals(floatArrayOf(0.6f, 0.8f), normalized, 0.0001f)
    }

    @Test
    fun normalizeReturnsOriginalZeroVector() {
        val zero = floatArrayOf(0f, 0f)

        val normalized = EmbeddingVectorUtils.normalize(zero)

        assertSame(zero, normalized)
    }

    @Test
    fun cosineHandlesMatchingZeroAndOrthogonalVectors() {
        assertEquals(1f, EmbeddingVectorUtils.cosine(floatArrayOf(1f, 0f), floatArrayOf(2f, 0f)), 0.0001f)
        assertEquals(0f, EmbeddingVectorUtils.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
        assertEquals(0f, EmbeddingVectorUtils.cosine(floatArrayOf(), floatArrayOf()), 0.0001f)
    }

    @Test
    fun cosineRejectsDimensionMismatchInsteadOfSilentlyTruncating() {
        try {
            EmbeddingVectorUtils.cosine(floatArrayOf(), floatArrayOf(1f))
            fail("Expected IllegalArgumentException for 0-vs-1 dimension mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dimension"))
        }
        try {
            EmbeddingVectorUtils.cosine(floatArrayOf(1f, 0f, 99f), floatArrayOf(1f, 0f))
            fail("Expected IllegalArgumentException for 3-vs-2 dimension mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dimension"))
        }
    }

    @Test
    fun clampSimilarityMapsCosineRangeToUnitRange() {
        assertEquals(0f, EmbeddingVectorUtils.clampSimilarity(-1f), 0.0001f)
        assertEquals(0.5f, EmbeddingVectorUtils.clampSimilarity(0f), 0.0001f)
        assertEquals(1f, EmbeddingVectorUtils.clampSimilarity(1f), 0.0001f)
        assertEquals(1f, EmbeddingVectorUtils.clampSimilarity(3f), 0.0001f)
    }
}
