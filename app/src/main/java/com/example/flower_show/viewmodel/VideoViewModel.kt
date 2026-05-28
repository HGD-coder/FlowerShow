package com.example.flower_show.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.repository.IVideoRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.util.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * VideoViewModel — MVI pattern / MVI 模式
 *
 * Single StateFlow<VideoState>. dispatch(intent) → reduce → newState.
 * 单 StateFlow，dispatch(Intent) → 换新 State。
 */
class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IVideoRepository = RepositoryFactory.getVideoRepository(application)

    // ── Single source of truth / 单一状态源 ──
    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state.asStateFlow()

    // ── Player (single instance) / 单播放器实例 ──
    val playerManager = VideoPlayerManager(application)

    private var currentPage = 0
    private var playingPosition = -1

    // ── Progress polling coroutine / 进度轮询协程 ──
    private var progressJob: Job? = null

    // ── Intent dispatcher / 意图分发 ──

    fun dispatch(intent: VideoIntent) {
        when (intent) {
            is VideoIntent.LoadFirstPage -> loadFirstPage()
            is VideoIntent.LoadNextPage -> loadNextPage()
            is VideoIntent.PlayPosition -> playPosition(intent.position)
            is VideoIntent.PausePlayer -> pausePlayer()
            is VideoIntent.ResumePlayer -> resumePlayer()
            is VideoIntent.TogglePlayPause -> playerManager.togglePlayPause()
            is VideoIntent.SeekTo -> playerManager.seekTo(intent.positionMs)
            is VideoIntent.DismissError -> _state.update { it.copy(error = null) }
            is VideoIntent.JumpToVideo -> jumpToVideo(intent.videoId)
            is VideoIntent.SetQuality -> playerManager.setQuality(intent.qualityName, intent.qualityUrl)
        }
    }

    init {
        loadFirstPage()
    }

    // ── Data loading / 数据加载 ──

    private fun loadFirstPage() {
        currentPage = 0
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.loadFeed(1, PAGE_SIZE)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(
                        items = result.data,
                        isLoading = false,
                        hasMore = result.data.isNotEmpty(),
                        currentPosition = 0,
                    ) }
                    currentPage = 1
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadNextPage() {
        val s = _state.value
        if (s.isLoading || !s.hasMore) return
        _state.update { it.copy(isLoading = true) }
        val nextPage = currentPage + 1
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.loadFeed(nextPage, PAGE_SIZE)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    val newItems = s.items + result.data
                    _state.update { it.copy(
                        items = newItems,
                        isLoading = false,
                        hasMore = result.data.isNotEmpty(),
                    ) }
                    currentPage = nextPage
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    // ── Player control / 播放控制 ──

    private fun jumpToVideo(videoId: String) {
        // Check if already loaded
        val existingIndex = _state.value.items.indexOfFirst {
            it is VideoItem && it.id == videoId
        }
        if (existingIndex >= 0) {
            _state.update { it.copy(targetVideoId = videoId, currentPosition = existingIndex) }
            playPosition(existingIndex)
            MetricsCollector.record("jump_to_video|already_loaded=true", 0)
            return
        }
        _state.update { it.copy(targetVideoId = videoId) }
        viewModelScope.launch(Dispatchers.IO) {
            var pagesLoaded = 0
            var found = false
            var nextPage = currentPage + 1
            while (!found) {
                val s = _state.value
                if (!s.hasMore) break
                when (val result = repository.loadFeed(nextPage, PAGE_SIZE)) {
                    is Result.Success -> {
                        pagesLoaded++
                        val newItems = s.items + result.data
                        val index = newItems.indexOfFirst { it is VideoItem && it.id == videoId }
                        if (index >= 0) {
                            found = true
                            withContext(Dispatchers.Main) {
                                _state.update { it.copy(
                                    items = newItems,
                                    isLoading = false,
                                    hasMore = result.data.isNotEmpty(),
                                    currentPosition = index,
                                    targetVideoId = null,
                                ) }
                                currentPage = nextPage
                                playPosition(index)
                            }
                        } else {
                            if (result.data.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _state.update { it.copy(isLoading = false, hasMore = false, targetVideoId = null) }
                                }
                                break
                            }
                            withContext(Dispatchers.Main) {
                                _state.update { it.copy(items = newItems, isLoading = false, hasMore = true) }
                            }
                            currentPage = nextPage
                            nextPage++
                        }
                    }
                    is Result.Error -> {
                        withContext(Dispatchers.Main) {
                            _state.update { it.copy(isLoading = false, targetVideoId = null) }
                        }
                        break
                    }
                    is Result.Loading -> {}
                }
            }
            Log.d("VideoViewModel", "jump_to_video | videoId=$videoId | pages_loaded=$pagesLoaded | found=$found")
            MetricsCollector.record("jump_to_video|already_loaded=false", pagesLoaded.toLong())
        }
    }

    // ── Player control / 播放控制 ──

    private fun playPosition(position: Int) {
        val item = _state.value.items.getOrNull(position) ?: return
        if (item !is VideoItem) { playerManager.pause(); stopProgress(); return }

        _state.update { it.copy(currentPosition = position) }

        if (!playerManager.isInitialized) {
            viewModelScope.launch(Dispatchers.IO) {
                playerManager.initialize()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isPlayerReady = true) }
                    playerManager.play(item.videoUrl)
                    playingPosition = position
                    startProgress()
                    preloadNextVideo()
                }
            }
            return
        }

        playerManager.play(item.videoUrl)
        playingPosition = position
        startProgress()
        preloadNextVideo()
    }

    private fun preloadNextVideo() {
        val items = _state.value.items
        val nextIndex = _state.value.currentPosition + 1
        val nextItem = items.getOrNull(nextIndex)
        if (nextItem is VideoItem) {
            playerManager.prefetchUrl(nextItem.videoUrl, viewModelScope)
        }
    }

    private fun pausePlayer() { playerManager.pause(); stopProgress() }

    private fun resumePlayer() {
        if (playingPosition >= 0) { playerManager.resume(); startProgress() }
    }

    // ── Progress polling / 进度轮询 ──

    private fun startProgress() {
        stopProgress()
        progressJob = viewModelScope.launch {
            while (isActive) {
                playerManager.notifyProgress()
                delay(200)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgress()
        playerManager.release()
        try {
            val report = MetricsCollector.summary()
            Log.d("FlowerMetrics", report)
            val file = java.io.File(getApplication<android.app.Application>().cacheDir, "flower_metrics.txt")
            file.writeText(report)
            Log.d("FlowerMetrics", "Report written to ${file.absolutePath}")
        } catch (_: Exception) {}
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}
