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

    @Synchronized
    fun addHistory(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isBlank()) return
        val history = getHistory().toMutableList()
        history.remove(cleaned)
        history.add(0, cleaned)
        if (history.size > MAX_HISTORY) {
            history.subList(MAX_HISTORY, history.size).clear()
        }
        saveHistory(history)
    }

    // getHistory 也会写 SharedPreferences（清除损坏的 V2 值、迁移 legacy 数据），
    // 必须与其他修改方法一样持锁，否则并发调用会交错丢失刚写入的记录。
    @Synchronized
    fun getHistory(): List<String> {
        // Try new JSON format first
        runCatching { prefs.getString(KEY_HISTORY_V2, null) }.getOrNull()?.let { json ->
            val parsed = try {
                val type = object : TypeToken<List<String?>>() {}.type
                val result: List<String?> = gson.fromJson(json, type) ?: emptyList()
                MetricsCollector.recordLabel("search_history_source", "json")
                sanitize(result)
            } catch (_: Exception) {
                MetricsCollector.recordLabel("search_history_source", "json_error")
                null
            }
            if (parsed != null) return parsed
            // V2 数据损坏：清除损坏值并继续走 legacy 迁移，
            // 而不是每次都返回空历史（否则要到下次 addHistory 才能自愈）。
            prefs.edit().remove(KEY_HISTORY_V2).apply()
        }
        // Fallback: read old StringSet, migrate to new format
        val legacySet = runCatching { prefs.getStringSet(KEY_HISTORY, emptySet()) }
            .getOrNull()
            ?: emptySet()
        if (legacySet.isNotEmpty()) {
            val migrated = sanitize(legacySet.toList())
            saveHistory(migrated)
            prefs.edit().remove(KEY_HISTORY).apply()
            MetricsCollector.recordLabel("search_history_source", "stringset")
            return migrated
        }
        MetricsCollector.recordLabel("search_history_source", "empty")
        return emptyList()
    }

    @Synchronized
    fun deleteHistory(keyword: String) {
        val cleaned = keyword.trim()
        val history = getHistory().toMutableList()
        history.remove(cleaned)
        saveHistory(history)
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).remove(KEY_HISTORY_V2).apply()
    }

    private fun saveHistory(history: List<String>) {
        prefs.edit().putString(KEY_HISTORY_V2, gson.toJson(sanitize(history))).apply()
    }

    private fun sanitize(history: List<String?>): List<String> {
        return history
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_HISTORY)
    }
}
