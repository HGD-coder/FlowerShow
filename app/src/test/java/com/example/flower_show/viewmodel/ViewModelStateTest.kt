package com.example.flower_show.viewmodel

import com.example.flower_show.model.QualityMode
import com.example.flower_show.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModelStateTest {
    @Test
    fun searchStateDefaultsToIdleEmptyState() {
        val state = SearchState()

        assertTrue(state.history.isEmpty())
        assertTrue(state.guessCandidates.isEmpty())
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
        assertEquals("", state.currentKeyword)
        assertNull(state.error)
    }

    @Test
    fun searchStateCopyCarriesSearchResultMetadata() {
        val state = SearchState().copy(
            history = listOf("food"),
            guessCandidates = listOf("shrimp"),
            isSearching = true,
            currentKeyword = "shrimp",
            error = "network",
        )

        assertEquals(listOf("food"), state.history)
        assertEquals(listOf("shrimp"), state.guessCandidates)
        assertTrue(state.isSearching)
        assertEquals("shrimp", state.currentKeyword)
        assertEquals("network", state.error)
    }

    @Test
    fun videoStateDefaultsToHomeFeedAutoQuality() {
        val state = VideoState()

        assertTrue(state.items.isEmpty())
        assertFalse(state.isLoading)
        assertTrue(state.hasMore)
        assertEquals(0, state.currentPosition)
        assertFalse(state.isPlayerReady)
        assertNull(state.error)
        assertNull(state.toastMessage)
        assertNull(state.targetVideoId)
        assertEquals(QualityMode.Auto, state.qualityMode)
        assertNull(state.currentQualityName)
        assertTrue(state.availableQualities.isEmpty())
    }

    @Test
    fun videoStateCopyCarriesPlaybackAndQualityFields() {
        val quality = VideoQuality("720p", "url", height = 720, bitrateKbps = 1_500)
        val state = VideoState().copy(
            isLoading = true,
            hasMore = false,
            currentPosition = 3,
            isPlayerReady = true,
            error = "failed",
            toastMessage = "quality changed",
            targetVideoId = "v1",
            qualityMode = QualityMode.Manual,
            currentQualityName = quality.name,
            availableQualities = listOf(quality),
        )

        assertTrue(state.isLoading)
        assertFalse(state.hasMore)
        assertEquals(3, state.currentPosition)
        assertTrue(state.isPlayerReady)
        assertEquals("failed", state.error)
        assertEquals("quality changed", state.toastMessage)
        assertEquals("v1", state.targetVideoId)
        assertEquals(QualityMode.Manual, state.qualityMode)
        assertEquals("720p", state.currentQualityName)
        assertEquals(listOf(quality), state.availableQualities)
    }

    @Test
    fun intentsKeepPayloadValues() {
        assertEquals("food", SearchIntent.Search("food").keyword)
        assertEquals("food", SearchIntent.DeleteHistory("food").keyword)
        assertEquals(7, VideoIntent.PlayPosition(7).position)
        assertEquals(1_200L, VideoIntent.SeekTo(1_200L).positionMs)
        assertEquals("v9", VideoIntent.JumpToVideo("v9").videoId)
        assertEquals("720p", VideoIntent.SelectManualQuality("720p", "url").name)
        assertEquals("url", VideoIntent.SelectManualQuality("720p", "url").url)
        assertEquals(300L, VideoIntent.ReportBuffering(300L).durationMs)
    }
}
