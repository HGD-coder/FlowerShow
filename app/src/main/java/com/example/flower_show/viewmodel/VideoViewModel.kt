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

    // ── Auto-quality tracking ──
    private val bufferingHistory = mutableListOf<Long>() // buffering start timestamps
    private var lastBufferedPercent: Int = 0             // from Progress events
    private var lastAutoSwitchMs: Long = 0
    private var qualityJob: Job? = null

    companion object {
        private const val TAG = "VideoViewModel"
        private const val PAGE_SIZE = 10

        // Downgrade thresholds
        private const val BUFFERING_WINDOW_MS = 30_000L
        private const val BUFFERING_MAX_COUNT = 2
        private const val BUFFERING_MAX_DURATION = 2000L
        // Upgrade thresholds
        private const val STABLE_UPGRADE_MS = 60_000L
        private const val UPGRADE_BUFFERED_PCT = 60       // must have >60% buffered
        // Anti-flap
        private const val COOLDOWN_MS = 30_000L
    }

    // ── Player callback: tracks buffering + bandwidth ──
    private val qualityCallback = PlayerCallback { event ->
        when (event) {
            is PlayerCallback.PlaybackEvent.BufferingStart -> {
                bufferingHistory.add(System.currentTimeMillis())
                val cutoff = System.currentTimeMillis() - BUFFERING_WINDOW_MS
                bufferingHistory.removeAll { it < cutoff }
            }
            is PlayerCallback.PlaybackEvent.BufferingEnd -> {
                dispatch(VideoIntent.ReportBuffering(event.durationMs))
            }
            is PlayerCallback.PlaybackEvent.Progress -> {
                lastBufferedPercent = event.bufferedPercent
            }
            else -> {}
        }
    }

    init {
        playerManager.addCallback(qualityCallback)
        loadFirstPage()
    }

    // ── Intent dispatch ──

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

    // ── Manual quality selection ──

    private fun selectManualQuality(name: String, url: String) {
        _state.update { it.copy(qualityMode = QualityMode.Manual, currentQualityName = name) }
        playerManager.setQuality(name, url)
        stopAutoQuality()
        Log.d(TAG, "Manual quality: $name")
    }

    private fun enableAutoQuality() {
        _state.update { it.copy(qualityMode = QualityMode.Auto) }
        resetBufferingState()
        evaluateAutoQuality()
        Log.d(TAG, "Auto quality enabled")
    }

    // ── Auto-quality: downgrade on buffering ──

    private fun onBufferingReported(durationMs: Long) {
        if (_state.value.qualityMode != QualityMode.Auto) return
        val qualities = _state.value.availableQualities
        if (qualities.isEmpty()) return

        val current = _state.value.currentQualityName
        val now = System.currentTimeMillis()
        if (now - lastAutoSwitchMs < COOLDOWN_MS) return

        val cutoff = now - BUFFERING_WINDOW_MS
        val recentBuffers = bufferingHistory.count { it >= cutoff }

        val shouldDowngrade = recentBuffers >= BUFFERING_MAX_COUNT || durationMs > BUFFERING_MAX_DURATION
        if (!shouldDowngrade) return

        val currentIdx = qualities.indexOfFirst { it.name == current }
        if (currentIdx < qualities.lastIndex) {
            val downgrade = qualities[currentIdx + 1]
            applyQualitySwitch(downgrade, "降级", true)
        }
    }

    // ── Auto-quality: upgrade on stable playback ──

    private fun evaluateAutoQuality() {
        if (_state.value.qualityMode != QualityMode.Auto) return
        qualityJob?.cancel()
        qualityJob = viewModelScope.launch {
            delay(STABLE_UPGRADE_MS)
            val s = _state.value
            val qualities = s.availableQualities
            if (qualities.isEmpty()) return@launch

            val currentIdx = qualities.indexOfFirst { it.name == s.currentQualityName }
            if (currentIdx <= 0) return@launch // already at highest

            val now = System.currentTimeMillis()
            if (now - lastAutoSwitchMs < COOLDOWN_MS) return@launch

            // Must have no recent buffering AND sufficient buffer ahead
            val cutoff = now - BUFFERING_WINDOW_MS
            val recentBuffers = bufferingHistory.count { it >= cutoff }
            if (recentBuffers > 0) return@launch
            if (lastBufferedPercent < UPGRADE_BUFFERED_PCT) return@launch

            val upgrade = qualities[currentIdx - 1]
            applyQualitySwitch(upgrade, "升级", false)
        }
    }

    private fun applyQualitySwitch(target: VideoQuality, direction: String, showToast: Boolean) {
        _state.update { it.copy(currentQualityName = target.name) }
        playerManager.setQuality(target.name, target.url)
        lastAutoSwitchMs = System.currentTimeMillis()
        MetricsCollector.recordLabel("auto_quality", "${direction}_${target.name}")
        Log.d(TAG, "Auto $direction: -> ${target.name}")
        if (showToast) {
            _state.update { it.copy(toastMessage = "网络波动，已自动切换到 ${target.name}") }
        }
    }

    private fun resetBufferingState() {
        bufferingHistory.clear()
        lastBufferedPercent = 0
    }

    private fun stopAutoQuality() {
        qualityJob?.cancel()
        qualityJob = null
    }

    // ── Data loading ──

    private fun loadFirstPage() {
        currentPage = 0
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.loadFeed(1, PAGE_SIZE)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(
                        items = result.data, isLoading = false,
                        hasMore = result.data.isNotEmpty(), currentPosition = 0,
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
                    _state.update { it.copy(items = newItems, isLoading = false, hasMore = result.data.isNotEmpty()) }
                    currentPage = nextPage
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    // ── Jump to video ──

    private fun jumpToVideo(videoId: String) {
        val existingIndex = _state.value.items.indexOfFirst { it is VideoItem && it.id == videoId }
        if (existingIndex >= 0) {
            _state.update { it.copy(targetVideoId = videoId, currentPosition = existingIndex) }
            playPosition(existingIndex)
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
                                _state.update { it.copy(items = newItems, isLoading = false,
                                    hasMore = result.data.isNotEmpty(), currentPosition = index, targetVideoId = null) }
                                currentPage = nextPage
                                playPosition(index)
                            }
                        } else {
                            if (result.data.isEmpty()) {
                                withContext(Dispatchers.Main) { _state.update { it.copy(isLoading = false, hasMore = false, targetVideoId = null) } }
                                break
                            }
                            withContext(Dispatchers.Main) { _state.update { it.copy(items = newItems, isLoading = false, hasMore = true) } }
                            currentPage = nextPage
                            nextPage++
                        }
                    }
                    is Result.Error -> {
                        withContext(Dispatchers.Main) { _state.update { it.copy(isLoading = false, targetVideoId = null) } }
                        break
                    }
                    is Result.Loading -> {}
                }
            }
        }
    }

    // ── Play position (fixed: plays highest quality URL when available) ──

    private fun playPosition(position: Int) {
        val item = _state.value.items.getOrNull(position) ?: return
        if (item !is VideoItem) { playerManager.pause(); stopProgress(); return }

        _state.update { it.copy(currentPosition = position) }
        resetBufferingState()

        // Parse qualities, sorted highest→lowest
        val qualities = item.qualityUrls?.map { (name, url) ->
            VideoQuality(name, url, name.removeSuffix("p").toIntOrNull() ?: 0)
        }?.sortedByDescending { it.height } ?: emptyList()

        // Best quality = first (highest height)
        val bestQuality = qualities.firstOrNull()
        val initialName = bestQuality?.name
        // Play the best quality URL — not the default videoUrl
        val playbackUrl = if (_state.value.qualityMode == QualityMode.Auto) {
            bestQuality?.url ?: item.videoUrl
        } else {
            // Manual mode: keep current quality if available, else fallback
            val currentName = _state.value.currentQualityName
            qualities.find { it.name == currentName }?.url ?: bestQuality?.url ?: item.videoUrl
        }

        _state.update { it.copy(availableQualities = qualities, currentQualityName = initialName) }

        if (!playerManager.isInitialized) {
            viewModelScope.launch(Dispatchers.IO) {
                playerManager.initialize()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isPlayerReady = true) }
                    playerManager.play(playbackUrl)
                    playingPosition = position
                    startProgress()
                    preloadNextVideo()
                    if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
                }
            }
            return
        }

        playerManager.play(playbackUrl)
        playingPosition = position
        startProgress()
        preloadNextVideo()
        if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
    }

    private fun preloadNextVideo() {
        val nextIndex = _state.value.currentPosition + 1
        val nextItem = _state.value.items.getOrNull(nextIndex)
        if (nextItem is VideoItem) playerManager.prefetchUrl(nextItem.videoUrl, viewModelScope)
    }

    private fun pausePlayer() { playerManager.pause(); stopProgress() }
    private fun resumePlayer() { if (playingPosition >= 0) { playerManager.resume(); startProgress() } }

    // ── Progress polling ──

    private fun startProgress() {
        stopProgress()
        progressJob = viewModelScope.launch {
            while (isActive) { playerManager.notifyProgress(); delay(200) }
        }
    }

    private fun stopProgress() { progressJob?.cancel(); progressJob = null }

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
