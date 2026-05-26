package com.example.flower_show.data.repository

import org.junit.Assert.*
import org.junit.Test
import com.example.flower_show.model.Result

class FakeVideoRepositoryTest {

    private val repo = FakeVideoRepository.getInstance(null)

    @Test
    fun loadFeed_page1_returnsCorrectSize() {
        val result = repo.loadFeed(1, 10)
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(10, data.size)
    }

    @Test
    fun loadFeed_page2_returnsRemaining() {
        val result = repo.loadFeed(2, 10)
        assertTrue(result.isSuccess)
    }

    @Test
    fun loadFeed_beyondRange_returnsEmpty() {
        val result = repo.loadFeed(100, 10)
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun search_exactTitleMatch() {
        val result = repo.search("梅西")
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isNotEmpty())
    }

    @Test
    fun search_noMatch_returnsEmpty() {
        val result = repo.search("不存在的内容XYZ123")
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun getRecommendWords_validId() {
        assertTrue(repo.getRecommendWords("v001").isNotEmpty())
    }

    @Test
    fun getRecommendWords_invalidId() {
        assertTrue(repo.getRecommendWords("nonexistent").isEmpty())
    }
}
