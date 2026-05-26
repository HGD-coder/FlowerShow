package com.example.flower_show.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.repository.IVideoRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.VideoPlayerManager
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
                }
            }
            return
        }

        playerManager.play(item.videoUrl)
        playingPosition = position
        startProgress()
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
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}
