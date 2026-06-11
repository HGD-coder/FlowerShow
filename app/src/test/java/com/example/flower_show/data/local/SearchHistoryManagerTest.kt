package com.example.flower_show.data.local

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SearchHistoryManager using FakeSharedPreferences (no Mockito needed).
 */
class SearchHistoryManagerTest {

    private fun createManager(): SearchHistoryManager {
        val prefs = FakeSharedPreferences()
        val context = object : android.app.Application() {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        return SearchHistoryManager(context)
    }

    // ── Positive tests ──

    @Test
    fun getHistory_empty_returnsEmptyList() {
        val manager = createManager()
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun addHistory_thenGetHistory_containsKeyword() {
        val manager = createManager()
        manager.addHistory("梅西")
        assertEquals(listOf("梅西"), manager.getHistory())
    }

    @Test
    fun addHistory_multipleKeywords_orderedByRecency() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.addHistory("足球")
        manager.addHistory("篮球")
        assertEquals(listOf("篮球", "足球", "梅西"), manager.getHistory())
    }

    @Test
    fun addHistory_duplicateKeyword_movesToFront() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.addHistory("足球")
        manager.addHistory("梅西") // duplicate → move to front
        assertEquals(listOf("梅西", "足球"), manager.getHistory())
    }

    @Test
    fun deleteHistory_existingKeyword_removesIt() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.addHistory("足球")
        manager.deleteHistory("梅西")
        assertEquals(listOf("足球"), manager.getHistory())
    }

    @Test
    fun clearAll_removesEverything() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.addHistory("足球")
        manager.clearAll()
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun addHistory_persistsAcrossNewManager() {
        val prefs = FakeSharedPreferences()
        val context = object : android.app.Application() {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }

        val manager1 = SearchHistoryManager(context)
        manager1.addHistory("梅西")

        val manager2 = SearchHistoryManager(context)
        assertEquals(listOf("梅西"), manager2.getHistory())
    }

    // ── Boundary tests ──

    @Test
    fun addHistory_maxCount_trimsQuietly() {
        val manager = createManager()
        // Add 25 items (max = 20)
        (1..25).forEach { manager.addHistory("词$it") }
        val history = manager.getHistory()
        assertTrue(history.size <= 20)
        assertEquals("词25", history.first()) // newest at front
    }

    @Test
    fun addHistory_blankKeyword_isIgnored() {
        val manager = createManager()
        manager.addHistory("")
        manager.addHistory("   ")
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun addHistory_mixedCase_handledAsIs() {
        val manager = createManager()
        manager.addHistory("iPhone")
        manager.addHistory("iphone")
        // Both stored as-is (case preserved)
        assertEquals(2, manager.getHistory().size)
    }

    @Test
    fun deleteHistory_nonExistent_noEffect() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.deleteHistory("足球") // not in list
        assertEquals(listOf("梅西"), manager.getHistory())
    }

    // ── Negative/reverse tests ──

    @Test
    fun deleteHistory_lastItem_returnsEmpty() {
        val manager = createManager()
        manager.addHistory("梅西")
        manager.deleteHistory("梅西")
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun addHistory_emojiKeyword_works() {
        val manager = createManager()
        manager.addHistory("😄🔥")
        assertEquals(listOf("😄🔥"), manager.getHistory())
    }
}

/**
 * Minimal fake SharedPreferences for unit tests — no Android dependency.
 */
class FakeSharedPreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = store
    override fun getString(key: String?, defValue: String?): String? =
        store[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = store[key]
        @Suppress("UNCHECKED_CAST")
        return (value as? MutableSet<String>) ?: defValues
    }
    override fun getInt(key: String?, defValue: Int): Int =
        (store[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long =
        (store[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float =
        (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (store[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(store)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

class FakeEditor(private val store: MutableMap<String, Any?>) : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        store[key!!] = value; return this
    }
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
        store[key!!] = values; return this
    }
    override fun putInt(key: String?, value: Int): SharedPreferences.Editor { store[key!!] = value; return this }
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor { store[key!!] = value; return this }
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { store[key!!] = value; return this }
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { store[key!!] = value; return this }
    override fun remove(key: String?): SharedPreferences.Editor { store.remove(key!!); return this }
    override fun clear(): SharedPreferences.Editor { store.clear(); return this }
    override fun commit(): Boolean = true
    override fun apply() {}
}
