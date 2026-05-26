package com.example.flower_show.viewmodel

import com.example.flower_show.model.CardItem

/**
 * SearchState — Single source of truth for search / 搜索全部状态
 */
data class SearchState(
    val history: List<String> = emptyList(),
    val results: List<CardItem> = emptyList(),
    val isSearching: Boolean = false,
    val currentKeyword: String = "",
    val error: String? = null,
)
