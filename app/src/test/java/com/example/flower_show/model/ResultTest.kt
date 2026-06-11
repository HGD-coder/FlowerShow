package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test

class ResultTest {

    // ── Positive tests ──

    @Test
    fun success_containsData() {
        val result = Result.success(42)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun success_nullData_isValid() {
        val result = Result.success(null)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun success_stringData_containsString() {
        val result = Result.success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun success_listData_containsList() {
        val result = Result.success(listOf(1, 2, 3))
        assertEquals(3, result.getOrNull()?.size)
    }

    @Test
    fun error_containsMessage() {
        val result = Result.error("fail")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertFalse(result.isLoading)
        assertEquals("fail", (result as Result.Error).message)
        assertNull(result.getOrNull())
    }

    @Test
    fun loading_isEmptyState() {
        val result = Result.loading()
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
        assertNull(result.getOrNull())
    }

    // ── Boundary tests ──

    @Test
    fun success_emptyString_returnsEmptyString() {
        val result = Result.success("")
        assertEquals("", result.getOrNull())
    }

    @Test
    fun error_emptyMessage_isValid() {
        val result = Result.error("")
        assertTrue(result.isError)
        assertEquals("", (result as Result.Error).message)
    }

    @Test
    fun errorChineseMessage_works() {
        val result = Result.error("加载失败")
        assertEquals("加载失败", (result as Result.Error).message)
    }

    @Test
    fun success_withComplexObject_returnsSameReference() {
        data class Complex(val a: Int, val b: String, val c: List<Double>)
        val obj = Complex(1, "x", listOf(1.0, 2.0))
        val result = Result.success(obj)
        assertSame(obj, result.getOrNull())
    }

    @Test
    fun successIntMax_isValid() {
        val result = Result.success(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, result.getOrNull())
    }

    // ── Negative/reverse tests ──

    @Test
    fun getOrNull_onSuccess_isNotNull() {
        assertNotNull(Result.success("x").getOrNull())
    }

    @Test
    fun getOrNull_onError_isNull() {
        assertNull(Result.error("x").getOrNull())
    }

    @Test
    fun getOrNull_onLoading_isNull() {
        assertNull(Result.loading().getOrNull())
    }

    @Test
    fun error_isNeverSuccess() {
        assertFalse(Result.error("any").isSuccess)
    }

    @Test
    fun loading_isNeverSuccessOrError() {
        val r = Result.loading()
        assertFalse(r.isSuccess)
        assertFalse(r.isError)
    }

    @Test
    fun success_isNeverErrorOrLoading() {
        val r = Result.success("x")
        assertFalse(r.isError)
        assertFalse(r.isLoading)
    }
}
