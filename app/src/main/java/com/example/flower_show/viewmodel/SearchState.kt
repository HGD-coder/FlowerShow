package com.example.flower_show.viewmodel

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.DeliveryContext
import com.example.flower_show.model.SearchGuess

/**
 * SearchState — Single source of truth for search / 搜索全部状态
 */
data class SearchState(
    val history: List<String> = emptyList(),
    val guesses: List<SearchGuess> = emptyList(),
    val guessServeSessionId: String? = null,
    val guessNextCursor: String? = null,
    val guessHasMore: Boolean = true,
    val isLoadingGuesses: Boolean = false,
    val guessError: String? = null,
    val results: List<CardItem> = emptyList(),
    val resultDeliveries: List<DeliveryContext> = emptyList(),
    val resultNextCursor: String? = null,
    val resultHasMore: Boolean = false,
    val isLoadingMoreResults: Boolean = false,
    val isSearching: Boolean = false,
    val currentKeyword: String = "",
    val error: String? = null,
)
