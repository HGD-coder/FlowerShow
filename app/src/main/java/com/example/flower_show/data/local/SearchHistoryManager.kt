package com.example.flower_show.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.flower_show.util.MetricsCollector
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SearchHistoryManager - Persist search history via SharedPreferences
 * Stores up to 20 most recent keywords (order-preserving via JSON list).
 */
class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_HISTORY = "search_keywords"           // old StringSet (legacy)
        private const val KEY_HISTORY_V2 = "search_keywords_json"   // new JSON list
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
        // Try new JSON format first
        prefs.getString(KEY_HISTORY_V2, null)?.let { json ->
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                val result: List<String> = gson.fromJson(json, type) ?: emptyList()
                MetricsCollector.recordLabel("search_history_source", "json")
                result
            } catch (_: Exception) {
                emptyList<String>().also {
                    MetricsCollector.recordLabel("search_history_source", "json_error")
                }
            }
        }
        // Fallback: read old StringSet, migrate to new format
        val legacySet = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        if (legacySet.isNotEmpty()) {
            val migrated = legacySet.toList()
            saveHistory(migrated)
            prefs.edit().remove(KEY_HISTORY).apply()
            MetricsCollector.recordLabel("search_history_source", "stringset")
            return migrated
        }
        MetricsCollector.recordLabel("search_history_source", "empty")
        return emptyList()
    }

    fun deleteHistory(keyword: String) {
        val history = getHistory().toMutableList()
        history.remove(keyword)
        saveHistory(history)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).remove(KEY_HISTORY_V2).apply()
    }

    private fun saveHistory(history: List<String>) {
        prefs.edit().putString(KEY_HISTORY_V2, gson.toJson(history)).apply()
    }
}
