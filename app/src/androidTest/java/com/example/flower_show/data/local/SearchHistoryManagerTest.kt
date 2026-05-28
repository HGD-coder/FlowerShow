package com.example.flower_show.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchHistoryManagerTest {

    private lateinit var manager: SearchHistoryManager

    @Before
    fun setUp() {
        manager = SearchHistoryManager(ApplicationProvider.getApplicationContext())
        manager.clearAll()
    }

    @Test
    fun addHistory_orderPreserved() {
        manager.addHistory("c")
        manager.addHistory("b")
        manager.addHistory("a")
        val history = manager.getHistory()
        assertEquals(listOf("a", "b", "c"), history)
    }

    @Test
    fun addHistory_duplicateMovesToFront() {
        manager.addHistory("a")
        manager.addHistory("b")
        manager.addHistory("a")
        val history = manager.getHistory()
        assertEquals(listOf("a", "b"), history)
    }

    @Test
    fun addHistory_maxSizeEnforced() {
        repeat(25) { manager.addHistory("item_$it") }
        val history = manager.getHistory()
        assertEquals(20, history.size)
    }

    @Test
    fun deleteHistory_removesCorrectly() {
        manager.addHistory("a")
        manager.addHistory("b")
        manager.addHistory("c")
        manager.deleteHistory("b")
        val history = manager.getHistory()
        assertEquals(listOf("c", "a"), history)
    }

    @Test
    fun clearAll_emptiesHistory() {
        manager.addHistory("a")
        manager.addHistory("b")
        manager.clearAll()
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun getHistory_emptyWhenNoData() {
        val history = manager.getHistory()
        assertNotNull(history)
        assertTrue(history.isEmpty())
    }
}
