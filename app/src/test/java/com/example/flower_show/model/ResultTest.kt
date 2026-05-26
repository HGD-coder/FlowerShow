package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test

class ResultTest {

    @Test
    fun success_containsData() {
        val result = Result.success(42)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun error_containsMessage() {
        val result = Result.error("fail")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertEquals("fail", (result as Result.Error).message)
        assertNull(result.getOrNull())
    }

    @Test
    fun loading_isEmptyState() {
        val result = Result.loading()
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
    }
}
