package com.example.flower_show.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.repository.ISearchRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.DeliveredCard
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * SearchViewModel — MVI pattern / MVI 模式
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ISearchRepository = RepositoryFactory.getSearchRepository(application)

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchRequestId = 0L
    private var guessRequestId = 0L
    private val eventSequence = AtomicLong()

    fun dispatch(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> search(intent.keyword)
            SearchIntent.LoadNextGuesses -> loadGuessCandidates(refresh = false)
            SearchIntent.LoadMoreResults -> loadMoreResults()
            is SearchIntent.SuggestionClick -> reportSuggestionClick(intent.suggestionId)
            is SearchIntent.LoadHistory -> loadHistory()
            is SearchIntent.DeleteHistory -> deleteHistory(intent.keyword)
            is SearchIntent.ClearHistory -> clearHistory()
            is SearchIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    init {
        loadGuessCandidates(refresh = true)
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
                resultDeliveries = emptyList(),
                resultNextCursor = null,
                resultHasMore = false,
                isLoadingMoreResults = false,
                isSearching = true,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 历史记录持久化是 SharedPreferences 写盘，放在 IO 线程，
            // 避免在搜索提交路径上做主线程磁盘 IO。
            runCatching { repository.addHistory(trimmed) }
                .onFailure { Log.w(TAG, "Failed to persist search history", it) }
            reportEvent(type = "search_submit", query = trimmed)
            when (val result = repository.search(trimmed, cursor = null, pageSize = SearchPageSize)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (requestId == searchRequestId) {
                        _state.update {
                            it.copy(
                                results = result.data.items.map { delivered -> delivered.card },
                                resultDeliveries = result.data.items.map { delivered -> delivered.delivery },
                                resultNextCursor = result.data.nextCursor,
                                resultHasMore = result.data.hasMore,
                                isSearching = false,
                            )
                        }
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
        // SharedPreferences 首次访问会同步加载 XML 文件，放到 IO 线程执行。
        viewModelScope.launch(Dispatchers.IO) {
            val history = runCatching { repository.getHistory() }
                .onFailure { Log.w(TAG, "Failed to load search history", it) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                _state.update { it.copy(history = history) }
            }
        }
    }

    private fun loadGuessCandidates(refresh: Boolean) {
        val current = _state.value
        if (current.isLoadingGuesses) return
        if (!refresh && (!current.guessHasMore || current.guessNextCursor.isNullOrBlank())) return
        val requestId = ++guessRequestId
        val sessionId = if (refresh) null else current.guessServeSessionId
        val cursor = if (refresh) null else current.guessNextCursor
        _state.update { it.copy(isLoadingGuesses = true, guessError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (
                val result = repository.getGuessCandidates(
                    cursor = cursor,
                    serveSessionId = sessionId,
                    pageSize = GuessPageSize,
                    refresh = refresh,
                )
            ) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (requestId != guessRequestId) return@withContext
                    _state.update {
                        it.copy(
                            guesses = result.data.items,
                            guessServeSessionId = result.data.serveSessionId,
                            guessNextCursor = result.data.nextCursor,
                            guessHasMore = result.data.hasMore,
                            isLoadingGuesses = false,
                        )
                    }
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    if (requestId == guessRequestId) {
                        _state.update {
                            it.copy(isLoadingGuesses = false, guessError = result.message)
                        }
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun loadMoreResults() {
        val current = _state.value
        val cursor = current.resultNextCursor
        if (
            current.isSearching ||
            current.isLoadingMoreResults ||
            !current.resultHasMore ||
            cursor.isNullOrBlank() ||
            current.currentKeyword.isBlank()
        ) {
            return
        }
        val requestId = searchRequestId
        val query = current.currentKeyword
        _state.update { it.copy(isLoadingMoreResults = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.search(query, cursor, SearchPageSize)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (requestId != searchRequestId || _state.value.currentKeyword != query) {
                        return@withContext
                    }
                    _state.update {
                        // 分页间可能有内容重叠（服务端重排/游标不稳），按内容身份去重，
                        // 与首页 feed 的 mergeFeedItems 行为保持一致，避免重复卡片。
                        val existing = it.results.mapTo(mutableSetOf(), CardItem::searchIdentity)
                        val accepted = mutableListOf<DeliveredCard>()
                        result.data.items.forEach { delivered ->
                            if (existing.add(delivered.card.searchIdentity())) {
                                accepted += delivered
                            }
                        }
                        // 整页都被去重掉时必须停止分页：cursor 只前移但不产生新内容，
                        // 自动加载触发器以 cursor 为 key，继续推进会陷入背靠背请求循环
                        // （VideoViewModel.loadNextPage 的 canLoadMore 有等价防护）。
                        val acceptedNothing = accepted.isEmpty()
                        it.copy(
                            results = it.results + accepted.map { delivered -> delivered.card },
                            resultDeliveries =
                                it.resultDeliveries + accepted.map { delivered -> delivered.delivery },
                            resultNextCursor = if (acceptedNothing) null else result.data.nextCursor,
                            resultHasMore = result.data.hasMore && !acceptedNothing,
                            isLoadingMoreResults = false,
                        )
                    }
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    if (requestId == searchRequestId) {
                        _state.update {
                            it.copy(isLoadingMoreResults = false, error = result.message)
                        }
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun reportSuggestionClick(suggestionId: String) {
        val suggestion = _state.value.guesses.firstOrNull { it.id == suggestionId } ?: return
        reportEvent(
            type = "suggestion_click",
            exposureToken = suggestion.exposureToken,
            suggestionId = suggestion.id,
            query = suggestion.text,
        )
    }

    private fun reportEvent(
        type: String,
        exposureToken: String? = null,
        suggestionId: String? = null,
        query: String? = null,
    ) {
        val event = RecommendationEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            occurredAtMs = System.currentTimeMillis(),
            exposureToken = exposureToken,
            suggestionId = suggestionId,
            query = query,
            sequence = eventSequence.incrementAndGet(),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.reportEvents(listOf(event))
            if (result is Result.Error) Log.w(TAG, "Failed to report $type: ${result.message}")
        }
    }

    private fun deleteHistory(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteHistory(keyword) }
                .onFailure { Log.w(TAG, "Failed to delete search history", it) }
        }
        loadHistory()
    }

    private fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.clearHistory() }
                .onFailure { Log.w(TAG, "Failed to clear search history", it) }
        }
        loadHistory()
    }

    private companion object {
        const val TAG = "SearchViewModel"
        const val GuessPageSize = 8
        const val SearchPageSize = 20
    }
}

/**
 * 搜索结果分页去重用的内容身份，与首页 feed 的去重规则保持一致。
 */
private fun CardItem.searchIdentity(): String = when (this) {
    is VideoItem -> "video:${id.ifBlank { videoUrl }}"
    is ImageCardItem -> "image:${id.ifBlank { imageUrl }}"
    is AlbumCardItem -> "album:${id.ifBlank { slides.joinToString("|") { it.mediaUrl } }}"
    CardItem.TypeVideo -> "type:video"
    CardItem.TypeImage -> "type:image"
    CardItem.TypeAlbum -> "type:album"
}
