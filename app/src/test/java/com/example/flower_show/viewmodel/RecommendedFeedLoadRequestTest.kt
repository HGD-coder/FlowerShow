package com.example.flower_show.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedFeedLoadRequestTest {
    @Test
    fun firstLoadAndActiveRefreshBothStartFreshSessions() {
        val firstLoad = RecommendedFeedLoadRequest.freshSession()
        val activeRefresh = RecommendedFeedLoadRequest.freshSession()

        listOf(firstLoad, activeRefresh).forEach { request ->
            assertNull(request.cursor)
            assertNull(request.serveSessionId)
            assertTrue(request.refresh)
        }
    }

    @Test
    fun nextPageKeepsCursorAndSessionWithoutRefresh() {
        val request = RecommendedFeedLoadRequest.nextPage(
            cursor = "next-cursor",
            serveSessionId = "serve-session",
        )

        assertEquals("next-cursor", request.cursor)
        assertEquals("serve-session", request.serveSessionId)
        assertFalse(request.refresh)
    }
}
