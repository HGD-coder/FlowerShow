package com.example.flower_show.viewmodel

import android.app.Application
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
        _state.update { it.copy(currentKeyword = trimmed, isSearching = true, error = null) }
        repository.addHistory(trimmed)

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.search(trimmed)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(results = result.data, isSearching = false) }
                    loadHistory()
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isSearching = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadHistory() {
        _state.update { it.copy(history = repository.getHistory()) }
    }

    private fun deleteHistory(keyword: String) {
        repository.deleteHistory(keyword)
        loadHistory()
    }

    private fun clearHistory() {
        repository.clearHistory()
        loadHistory()
    }
}
