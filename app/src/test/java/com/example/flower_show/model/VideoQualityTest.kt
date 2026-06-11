package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test

class VideoQualityTest {

    // ── Positive tests ──

    @Test
    fun qualityConstructor_allFieldsSetCorrectly() {
        val q = VideoQuality("720p", "https://example.com/720.mp4", 720, 1500)
        assertEquals("720p", q.name)
        assertEquals("https://example.com/720.mp4", q.url)
        assertEquals(720, q.height)
        assertEquals(1500, q.bitrateKbps)
    }

    @Test
    fun qualityEquality_sameFields_areEqual() {
        val a = VideoQuality("720p", "url", 720, 1500)
        val b = VideoQuality("720p", "url", 720, 1500)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun qualityEquality_differentFields_notEqual() {
        val a = VideoQuality("720p", "url", 720, 1500)
        val b = VideoQuality("480p", "url", 480, 900)
        assertNotEquals(a, b)
    }

    @Test
    fun qualityCopy_preservesFields() {
        val original = VideoQuality("720p", "url", 720, 1500)
        val copy = original.copy(name = "480p")
        assertEquals("480p", copy.name)
        assertEquals("url", copy.url)
        assertEquals(720, copy.height)
    }

    @Test
    fun qualityNameComparison_sortsCorrectly() {
        val qualities = listOf(
            VideoQuality("480p", "a", 480, 900),
            VideoQuality("720p", "b", 720, 1500),
            VideoQuality("360p", "c", 360, 500),
        )
        val sorted = qualities.sortedByDescending { it.height }
        assertEquals(listOf("720p", "480p", "360p"), sorted.map { it.name })
    }

    // ── Boundary tests ──

    @Test
    fun qualityWithZeroHeight_zeroBitrate_isValid() {
        val q = VideoQuality("auto", "", 0, 0)
        assertEquals(0, q.height)
        assertEquals(0, q.bitrateKbps)
    }

    @Test
    fun qualityWithVeryHighBitrate_isValid() {
        val q = VideoQuality("8K", "url", 4320, Int.MAX_VALUE)
        assertEquals(4320, q.height)
        assertTrue(q.bitrateKbps > 0)
    }

    @Test
    fun qualityEmptyName_emptyUrl_isValid() {
        val q = VideoQuality("", "", 720, 0)
        assertEquals("", q.name)
    }

    // ── Negative/reverse tests ──

    @Test
    fun quality_differentUrlByName_onlyNameMattersForEqualityCheck() {
        // data class: all fields participate in equals
        val a = VideoQuality("720p", "url_a", 720, 1500)
        val b = VideoQuality("720p", "url_b", 720, 1500)
        assertNotEquals(a, b) // different url → not equal
    }

    @Test
    fun qualityListFilterByHeight_returnsCorrectSubset() {
        val qualities = listOf(
            VideoQuality("1080p", "a", 1080, 2500),
            VideoQuality("720p", "b", 720, 1500),
            VideoQuality("480p", "c", 480, 900),
        )
        val filtered = qualities.filter { it.height in 720..1080 }
        assertEquals(2, filtered.size)
    }
}
