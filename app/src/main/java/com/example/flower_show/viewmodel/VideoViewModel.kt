package com.example.flower_show.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.repository.IVideoRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.QualityMode
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem
import com.example.flower_show.model.VideoQuality
import com.example.flower_show.player.PlayerCallback
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

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IVideoRepository = RepositoryFactory.getVideoRepository(application)

    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state.asStateFlow()

    val playerManager = VideoPlayerManager(application)

    private var currentPage = 0
    private var playingPosition = -1
    private var progressJob: Job? = null

    // ── Auto-quality tracking / 自动画质策略状态 ──
    private val bufferingHistory = mutableListOf<Long>() // timestamps of buffering events
    private var lastAutoSwitchMs: Long = 0
    private var qualityJob: Job? = null

    companion object {
        private const val TAG = "VideoViewModel"
        private const val PAGE_SIZE = 10

        // Auto-quality thresholds
        private const val BUFFERING_WINDOW_MS = 30_000L   // 30s sliding window
        private const val BUFFERING_MAX_COUNT = 2          // max buffers before downgrade
        private const val BUFFERING_MAX_DURATION = 2000L   // single buffer > 2s triggers downgrade
        private const val STABLE_UPGRADE_MS = 60_000L      // 60s stable before considering upgrade
        private const val UPGRADE_BUFFERED_THRESHOLD = 60  // bufferedPercent > 60%
        private const val COOLDOWN_MS = 30_000L            // anti-flap cooldown
    }

    // ── Player callback for auto-quality / 缓冲事件监听 ──
    private val qualityCallback = PlayerCallback { event ->
        when (event) {
            is PlayerCallback.PlaybackEvent.BufferingStart -> {
                bufferingHistory.add(System.currentTimeMillis())
                // Prune old events outside the window
                val cutoff = System.currentTimeMillis() - BUFFERING_WINDOW_MS
                bufferingHistory.removeAll { it < cutoff }
            }
            is PlayerCallback.PlaybackEvent.BufferingEnd -> {
                dispatch(VideoIntent.ReportBuffering(event.durationMs))
            }
            else -> {}
        }
    }

    init {
        playerManager.addCallback(qualityCallback)
        loadFirstPage()
    }

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
            is VideoIntent.DismissToast -> _state.update { it.copy(toastMessage = null) }
            is VideoIntent.JumpToVideo -> jumpToVideo(intent.videoId)
            is VideoIntent.EnableAutoQuality -> enableAutoQuality()
            is VideoIntent.SelectManualQuality -> selectManualQuality(intent.name, intent.url)
            is VideoIntent.ReportBuffering -> onBufferingReported(intent.durationMs)
        }
    }

    // ── Quality: Manual selection / 手动选择清晰度 ──

    private fun selectManualQuality(name: String, url: String) {
        _state.update { it.copy(qualityMode = QualityMode.Manual, currentQualityName = name) }
        playerManager.setQuality(name, url)
        stopAutoQuality()
        Log.d(TAG, "Manual quality: $name")
    }

    private fun enableAutoQuality() {
        _state.update { it.copy(qualityMode = QualityMode.Auto) }
        // Re-evaluate immediately
        evaluateAutoQuality()
        Log.d(TAG, "Auto quality enabled")
    }

    // ── Quality: Auto strategy / 自动画质策略 ──

    private fun onBufferingReported(durationMs: Long) {
        if (_state.value.qualityMode != QualityMode.Auto) return
        val qualities = _state.value.availableQualities
        if (qualities.isEmpty()) return

        val current = _state.value.currentQualityName
        val now = System.currentTimeMillis()

        // Check cooldown
        if (now - lastAutoSwitchMs < COOLDOWN_MS) return

        // Count recent buffers
        val cutoff = now - BUFFERING_WINDOW_MS
        val recentBuffers = bufferingHistory.count { it >= cutoff }

        // Determine if we should downgrade
        val shouldDowngrade = recentBuffers >= BUFFERING_MAX_COUNT || durationMs > BUFFERING_MAX_DURATION
        if (!shouldDowngrade) return

        // Find current index and downgrade one step
        val currentIdx = qualities.indexOfFirst { it.name == current }
        if (currentIdx < qualities.lastIndex) {
            val downgrade = qualities[currentIdx + 1] // lower quality = higher index
            _state.update { it.copy(currentQualityName = downgrade.name) }
            playerManager.setQuality(downgrade.name, downgrade.url)
            lastAutoSwitchMs = now
            MetricsCollector.recordLabel("auto_quality", "downgrade_${downgrade.name}")
            _state.update { it.copy(toastMessage = "网络波动，已自动切换到 ${downgrade.name}") }
            Log.d(TAG, "Auto downgrade: $current -> ${downgrade.name}")
        }
    }

    private fun evaluateAutoQuality() {
        if (_state.value.qualityMode != QualityMode.Auto) return
        qualityJob?.cancel()
        qualityJob = viewModelScope.launch {
            delay(STABLE_UPGRADE_MS) // Wait for stable period
            val s = _state.value
            val qualities = s.availableQualities
            if (qualities.isEmpty()) return@launch

            val currentIdx = qualities.indexOfFirst { it.name == s.currentQualityName }
            if (currentIdx <= 0) return@launch // Already at highest quality

            // Check: recent buffering is clean, buffer is sufficient
            val cutoff = System.currentTimeMillis() - BUFFERING_WINDOW_MS
            val recentBuffers = bufferingHistory.count { it >= cutoff }
            if (recentBuffers > 0) return@launch // Still buffering recently

            val upgrade = qualities[currentIdx - 1] // higher quality = lower index
            val now = System.currentTimeMillis()
            if (now - lastAutoSwitchMs < COOLDOWN_MS) return@launch

            _state.update { it.copy(currentQualityName = upgrade.name) }
            playerManager.setQuality(upgrade.name, upgrade.url)
            lastAutoSwitchMs = now
            MetricsCollector.recordLabel("auto_quality", "upgrade_${upgrade.name}")
            Log.d(TAG, "Auto upgrade: ${s.currentQualityName} -> ${upgrade.name}")
        }
    }

    private fun stopAutoQuality() {
        qualityJob?.cancel()
        qualityJob = null
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
        val existingIndex = _state.value.items.indexOfFirst { it is VideoItem && it.id == videoId }
        if (existingIndex >= 0) {
            _state.update { it.copy(targetVideoId = videoId, currentPosition = existingIndex) }
            playPosition(existingIndex)
            MetricsCollector.record("jump_to_video|already_loaded=true", 0)
            return
        }
        _state.update { it.copy(targetVideoId = videoId) }
        viewModelScope.launch(Dispatchers.IO) {
            var nextPage = currentPage + 1
            var found = false
            while (!found) {
                val s = _state.value
                if (!s.hasMore) break
                when (val result = repository.loadFeed(nextPage, PAGE_SIZE)) {
                    is Result.Success -> {
                        val newItems = s.items + result.data
                        val index = newItems.indexOfFirst { it is VideoItem && it.id == videoId }
                        if (index >= 0) {
                            found = true
                            withContext(Dispatchers.Main) {
                                _state.update { it.copy(
                                    items = newItems, isLoading = false,
                                    hasMore = result.data.isNotEmpty(),
                                    currentPosition = index, targetVideoId = null,
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
        }
    }

    private fun playPosition(position: Int) {
        val item = _state.value.items.getOrNull(position) ?: return
        if (item !is VideoItem) { playerManager.pause(); stopProgress(); return }

        _state.update { it.copy(currentPosition = position) }

        // Parse available qualities and pick initial quality
        val qualities = item.qualityUrls?.map { (name, url) ->
            val height = name.removeSuffix("p").toIntOrNull() ?: 0
            VideoQuality(name, url, height)
        }?.sortedByDescending { it.height } ?: emptyList()
        val initialQuality = if (qualities.isNotEmpty()) qualities.first().name else null

        _state.update { it.copy(availableQualities = qualities, currentQualityName = initialQuality) }

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

    // ── Progress polling ──

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
        stopAutoQuality()
        playerManager.removeCallback(qualityCallback)
        playerManager.release()
        try {
            val report = MetricsCollector.summary()
            Log.d("FlowerMetrics", report)
            val file = java.io.File(getApplication<Application>().cacheDir, "flower_metrics.txt")
            file.writeText(report)
        } catch (_: Exception) {}
    }
}
