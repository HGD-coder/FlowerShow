package com.example.flower_show.data.local

import android.content.Context
import com.google.gson.Gson

object AssetSearchSuggestionLoader {
    private const val VIDEO_CONTENT_SEARCHES = "search/video_content_searches.json"
    private const val MULTIMODAL_SEARCH_SUGGESTIONS = "search/multimodal_search_suggestions.json"
    private val gson = Gson()

    fun loadVideoContentSearches(context: Context): Map<String, List<String>> {
        val root = readJsonMap(context, VIDEO_CONTENT_SEARCHES) ?: return emptyMap()
        val items = root["items"] as? List<*> ?: return emptyMap()
        return items.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val id = item["id"] as? String ?: return@mapNotNull null
            val searches = item["searches"].toSearchStrings()
            if (id.isBlank() || searches.isEmpty()) return@mapNotNull null
            id to searches
        }.toMap()
    }

    fun loadGlobalGuessSearches(context: Context): List<String> {
        val root = readJsonMap(context, MULTIMODAL_SEARCH_SUGGESTIONS) ?: return emptyList()
        return root["suggestions"].toSearchStrings()
    }

    private fun readJsonMap(context: Context, assetPath: String): Map<String, Any>? {
        return try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(reader, Map::class.java) as? Map<String, Any>
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Any?.toSearchStrings(): List<String> {
        val values = this as? List<*> ?: return emptyList()
        return values.mapNotNull { value ->
            when (value) {
                is String -> value
                is Map<*, *> -> value["keyword"] as? String
                else -> null
            }?.trim()?.takeIf { it.length >= 2 }
        }.distinct()
    }
}
