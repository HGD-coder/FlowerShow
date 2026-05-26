package com.example.flower_show.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * SearchHistoryManager - Persist search history via SharedPreferences
 * Stores up to 20 most recent keywords.
 */
class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "search_keywords"
        private const val MAX_HISTORY = 20
    }

    fun addHistory(keyword: String) {
        if (keyword.isBlank()) return
        val history = getHistory().toMutableList()
        history.remove(keyword)
        history.add(0, keyword)
        if (history.size > MAX_HISTORY) {
            history.subList(MAX_HISTORY, history.size).clear()
        }
        saveHistory(history)
    }

    fun getHistory(): List<String> {
        return prefs.getStringSet(KEY_HISTORY, emptySet())
            ?.toList()
            ?: emptyList()
    }

    fun deleteHistory(keyword: String) {
        val history = getHistory().toMutableList()
        history.remove(keyword)
        saveHistory(history)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(history: List<String>) {
        prefs.edit().putStringSet(KEY_HISTORY, history.toSet()).apply()
    }
}
