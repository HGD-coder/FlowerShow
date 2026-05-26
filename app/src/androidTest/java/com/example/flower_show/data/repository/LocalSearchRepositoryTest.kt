package com.example.flower_show.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class LocalSearchRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = LocalSearchRepository(context)

    @Test
    fun getHistory_initiallyEmpty() {
        // Clear first to ensure clean state
        repo.clearHistory()
        assertEquals(0, repo.getHistory().size)
    }

    @Test
    fun addHistory_and_getHistory() {
        repo.clearHistory()
        repo.addHistory("足球")
        repo.addHistory("篮球")
        val history = repo.getHistory()
        assertTrue(history.any { it.contains("足球") })
        assertTrue(history.any { it.contains("篮球") })
    }

    @Test
    fun search_returnsResults() {
        val result = repo.search("梅西")
        assertTrue(result.isSuccess)
    }
}
