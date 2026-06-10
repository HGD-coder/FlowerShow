package com.example.flower_show.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.repository.ISearchRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SearchViewModel — MVI pattern / MVI 模式
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ISearchRepository = RepositoryFactory.getSearchRepository(application)

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchRequestId = 0L

    fun dispatch(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> search(intent.keyword)
            is SearchIntent.LoadHistory -> loadHistory()
            is SearchIntent.DeleteHistory -> deleteHistory(intent.keyword)
            is SearchIntent.ClearHistory -> clearHistory()
            is SearchIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    init {
        loadHistory()
    }

    private fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val requestId = ++searchRequestId
        _state.update {
            it.copy(
                currentKeyword = trimmed,
                results = emptyList(),
                isSearching = true,
                error = null,
            )
        }
        runCatching { repository.addHistory(trimmed) }
            .onFailure { Log.w(TAG, "Failed to persist search history", it) }

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.search(trimmed)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (requestId == searchRequestId) {
                        _state.update { it.copy(results = result.data, isSearching = false) }
                        loadHistory()
                    }
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    if (requestId == searchRequestId) {
                        _state.update { it.copy(isSearching = false, error = result.message) }
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadHistory() {
        val history = runCatching { repository.getHistory() }
            .onFailure { Log.w(TAG, "Failed to load search history", it) }
            .getOrDefault(emptyList())
        _state.update { it.copy(history = history) }
    }

    private fun deleteHistory(keyword: String) {
        runCatching { repository.deleteHistory(keyword) }
            .onFailure { Log.w(TAG, "Failed to delete search history", it) }
        loadHistory()
    }

    private fun clearHistory() {
        runCatching { repository.clearHistory() }
            .onFailure { Log.w(TAG, "Failed to clear search history", it) }
        loadHistory()
    }

    private companion object {
        const val TAG = "SearchViewModel"
    }
}
