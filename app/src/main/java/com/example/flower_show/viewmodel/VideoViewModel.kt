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
import com.example.flower_show.model.VideoQualitySelector
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.player.VideoPreloadRequest
import com.example.flower_show.util.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IVideoRepository = RepositoryFactory.getVideoRepository(application)

    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state.asStateFlow()

    val playerManager = VideoPlayerManager(application)

    private var currentPage = 0
    private var playingPosition = -1
    private var hasHandledHomeEntry = false
    private var progressJob: Job? = null

    private val bufferingHistory = mutableListOf<Long>()
    private var lastPlayableBufferMs: Long = 0
    private var lastEstimatedBandwidthKbps: Int = 0
    private var stablePlaybackSinceMs: Long = 0
    private var lastAutoSwitchMs: Long = 0
    private var qualityJob: Job? = null

    companion object {
        private const val TAG = "VideoViewModel"
        private const val PAGE_SIZE = 10

        private const val BUFFERING_WINDOW_MS = 30_000L
        private const val BUFFERING_MAX_COUNT = 2
        private const val BUFFERING_MAX_DURATION = 2_000L
        private const val LOW_PLAYABLE_BUFFER_MS = 1_500L
        private const val DOWNGRADE_BANDWIDTH_HEADROOM = 0.90

        private const val STABLE_UPGRADE_MS = 60_000L
        private const val UPGRADE_PLAYABLE_BUFFER_MS = 5_000L
        private const val UPGRADE_BANDWIDTH_HEADROOM = 1.35
        private const val AUTO_EVALUATION_INTERVAL_MS = 5_000L

        private const val COOLDOWN_MS = 30_000L
        private const val PRELOAD_BEHIND_COUNT = 1
        private const val PRELOAD_AHEAD_COUNT = 3
    }

    private val qualityCallback = PlayerCallback { event ->
        when (event) {
            is PlayerCallback.PlaybackEvent.BufferingStart -> {
                bufferingHistory.add(System.currentTimeMillis())
                pruneBufferingHistory()
                stablePlaybackSinceMs = 0
                evaluateAutoDowngrade(reason = "buffering_start", bufferingDurationMs = 0L)
            }

            is PlayerCallback.PlaybackEvent.BufferingEnd -> {
                dispatch(VideoIntent.ReportBuffering(event.durationMs))
            }

            is PlayerCallback.PlaybackEvent.Progress -> {
                lastPlayableBufferMs = event.playableBufferMs
                lastEstimatedBandwidthKbps = event.estimatedBandwidthKbps
                updateStablePlaybackClock()
                evaluateAutoDowngrade(reason = "low_buffer_or_bandwidth", bufferingDurationMs = 0L)
            }

            else -> Unit
        }
    }

    init {
        playerManager.addCallback(qualityCallback)
        loadFirstPage(refreshOrder = true)
    }

    fun dispatch(intent: VideoIntent) {
        when (intent) {
            is VideoIntent.EnterHomeFeed -> enterHomeFeed()
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

    private fun selectManualQuality(name: String, url: String) {
        val target = _state.value.availableQualities.firstOrNull { it.name == name && it.url == url }
        if (target == null) {
            _state.update { it.copy(toastMessage = "清晰度不可用") }
            MetricsCollector.recordLabel("manual_quality", "unavailable_$name")
            return
        }

        stopAutoQuality()
        _state.update {
            it.copy(
                qualityMode = QualityMode.Manual,
                currentQualityName = target.name,
            )
        }
        playerManager.setQuality(target.name, target.url)
        refreshPreloadWindow()
        MetricsCollector.recordLabel("manual_quality", target.name)
        Log.d(TAG, "Manual quality: ${target.name}")
    }

    private fun enableAutoQuality() {
        _state.update { it.copy(qualityMode = QualityMode.Auto) }
        resetQualityRuntimeState()
        evaluateAutoQuality()
        Log.d(TAG, "Auto quality enabled")
    }

    private fun onBufferingReported(durationMs: Long) {
        stablePlaybackSinceMs = 0
        evaluateAutoDowngrade(reason = "buffering_duration", bufferingDurationMs = durationMs)
    }

    private fun evaluateAutoDowngrade(reason: String, bufferingDurationMs: Long) {
        val s = _state.value
        if (s.qualityMode != QualityMode.Auto) return
        val qualities = s.availableQualities
        if (qualities.size < 2) return

        val now = System.currentTimeMillis()
        if (now - lastAutoSwitchMs < COOLDOWN_MS) return

        pruneBufferingHistory(now)
        val recentBuffers = bufferingHistory.count { it >= now - BUFFERING_WINDOW_MS }
        val current = qualities.find { it.name == s.currentQualityName } ?: return
        val bandwidthTight = !VideoQualitySelector.hasBandwidthFor(
            quality = current,
            estimatedBandwidthKbps = lastEstimatedBandwidthKbps,
            headroom = DOWNGRADE_BANDWIDTH_HEADROOM,
        )
        val lowPlayableBuffer = lastPlayableBufferMs in 1 until LOW_PLAYABLE_BUFFER_MS
        val shouldDowngrade =
            recentBuffers >= BUFFERING_MAX_COUNT ||
                bufferingDurationMs > BUFFERING_MAX_DURATION ||
                (lowPlayableBuffer && bandwidthTight)

        if (!shouldDowngrade) return

        val downgrade = VideoQualitySelector.lowerThan(qualities, current.name) ?: return
        applyAutoQualitySwitch(downgrade, direction = "down", reason = reason, showToast = true)
    }

    private fun evaluateAutoQuality() {
        if (_state.value.qualityMode != QualityMode.Auto) return
        qualityJob?.cancel()
        qualityJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_EVALUATION_INTERVAL_MS)
                val s = _state.value
                if (s.qualityMode != QualityMode.Auto) return@launch

                val qualities = s.availableQualities
                if (qualities.size < 2) continue

                val now = System.currentTimeMillis()
                if (now - lastAutoSwitchMs < COOLDOWN_MS) continue

                pruneBufferingHistory(now)
                val recentBuffers = bufferingHistory.count { it >= now - BUFFERING_WINDOW_MS }
                if (recentBuffers > 0 || lastPlayableBufferMs < UPGRADE_PLAYABLE_BUFFER_MS) {
                    stablePlaybackSinceMs = 0
                    continue
                }

                val stableSince = stablePlaybackSinceMs.takeIf { it > 0 } ?: now.also {
                    stablePlaybackSinceMs = it
                }
                if (now - stableSince < STABLE_UPGRADE_MS) continue

                val upgrade = VideoQualitySelector.higherThan(qualities, s.currentQualityName) ?: continue
                val hasHeadroom = VideoQualitySelector.hasBandwidthFor(
                    quality = upgrade,
                    estimatedBandwidthKbps = lastEstimatedBandwidthKbps,
                    headroom = UPGRADE_BANDWIDTH_HEADROOM,
                )
                if (!hasHeadroom) continue

                applyAutoQualitySwitch(
                    target = upgrade,
                    direction = "up",
                    reason = "stable",
                    showToast = false,
                )
            }
        }
    }

    private fun applyAutoQualitySwitch(
        target: VideoQuality,
        direction: String,
        reason: String,
        showToast: Boolean,
    ) {
        _state.update { it.copy(currentQualityName = target.name) }
        playerManager.setQuality(target.name, target.url)
        lastAutoSwitchMs = System.currentTimeMillis()
        stablePlaybackSinceMs = 0
        refreshPreloadWindow()
        MetricsCollector.recordLabel("auto_quality", "${direction}_${target.name}_$reason")
        Log.d(TAG, "Auto quality $direction -> ${target.name}, reason=$reason")
        if (showToast) {
            _state.update { it.copy(toastMessage = "网络波动，已自动切换到 ${target.name}") }
        }
    }

    private fun resetQualityRuntimeState() {
        bufferingHistory.clear()
        lastPlayableBufferMs = 0
        lastEstimatedBandwidthKbps = 0
        stablePlaybackSinceMs = 0
    }

    private fun stopAutoQuality() {
        qualityJob?.cancel()
        qualityJob = null
    }

    private fun updateStablePlaybackClock() {
        val now = System.currentTimeMillis()
        pruneBufferingHistory(now)
        val hasRecentBuffering = bufferingHistory.any { it >= now - BUFFERING_WINDOW_MS }
        if (hasRecentBuffering || lastPlayableBufferMs < UPGRADE_PLAYABLE_BUFFER_MS) {
            stablePlaybackSinceMs = 0
        } else if (stablePlaybackSinceMs == 0L) {
            stablePlaybackSinceMs = now
        }
    }

    private fun pruneBufferingHistory(now: Long = System.currentTimeMillis()) {
        val cutoff = now - BUFFERING_WINDOW_MS
        bufferingHistory.removeAll { it < cutoff }
    }

    private fun enterHomeFeed() {
        val state = _state.value
        if (!hasHandledHomeEntry) {
            hasHandledHomeEntry = true
            if (state.items.isEmpty() && !state.isLoading) {
                loadFirstPage(refreshOrder = true)
            }
            return
        }

        if (!state.isLoading) {
            loadFirstPage(refreshOrder = true)
        }
    }

    private fun loadFirstPage(refreshOrder: Boolean = false) {
        if (refreshOrder) {
            repository.refreshFeedSession()
        }
        currentPage = 0
        _state.update { it.copy(isLoading = true, error = null, targetVideoId = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.loadFeed(1, PAGE_SIZE)) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            items = result.data,
                            isLoading = false,
                            hasMore = result.data.isNotEmpty(),
                            currentPosition = 0,
                        )
                    }
                    currentPage = 1
                }

                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }

                is Result.Loading -> Unit
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
                    _state.update {
                        it.copy(
                            items = newItems,
                            isLoading = false,
                            hasMore = result.data.isNotEmpty(),
                        )
                    }
                    currentPage = nextPage
                    refreshPreloadWindow()
                }

                is Result.Error -> withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun jumpToVideo(videoId: String) {
        val existingIndex = _state.value.items.indexOfFirst { it is VideoItem && it.id == videoId }
        if (existingIndex >= 0) {
            _state.update { it.copy(targetVideoId = null, currentPosition = existingIndex) }
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
                                _state.update {
                                    it.copy(
                                        items = newItems,
                                        isLoading = false,
                                        hasMore = result.data.isNotEmpty(),
                                        currentPosition = index,
                                        targetVideoId = null,
                                    )
                                }
                                currentPage = nextPage
                                playPosition(index)
                            }
                        } else {
                            if (result.data.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _state.update {
                                        it.copy(
                                            isLoading = false,
                                            hasMore = false,
                                            targetVideoId = null,
                                        )
                                    }
                                }
                                break
                            }
                            withContext(Dispatchers.Main) {
                                _state.update {
                                    it.copy(items = newItems, isLoading = false, hasMore = true)
                                }
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

                    is Result.Loading -> Unit
                }
            }
        }
    }

    private fun playPosition(position: Int) {
        val item = _state.value.items.getOrNull(position) ?: return
        if (item !is VideoItem) {
            playerManager.pause()
            stopProgress()
            return
        }

        val qualities = qualitiesFor(item)
        val targetQuality = selectPlaybackQuality(qualities)
        val playbackUrl = targetQuality?.url ?: item.videoUrl
        val displayName = targetQuality?.name

        _state.update {
            it.copy(
                currentPosition = position,
                availableQualities = qualities,
                currentQualityName = displayName,
            )
        }
        resetQualityRuntimeState()

        if (!playerManager.isInitialized) {
            viewModelScope.launch(Dispatchers.IO) {
                playerManager.initialize()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isPlayerReady = true) }
                    playerManager.play(playbackUrl, position)
                    playingPosition = position
                    startProgress()
                    refreshPreloadWindow()
                    if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
                }
            }
            return
        }

        playerManager.play(playbackUrl, position)
        playingPosition = position
        startProgress()
        refreshPreloadWindow()
        if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
    }

    private fun qualitiesFor(item: VideoItem): List<VideoQuality> {
        return VideoQualitySelector.from(item.qualityUrls)
    }

    private fun selectPlaybackQuality(qualities: List<VideoQuality>): VideoQuality? {
        return VideoQualitySelector.chooseForMode(
            qualities = qualities,
            mode = _state.value.qualityMode,
            currentQualityName = _state.value.currentQualityName,
        )
    }

    private fun playbackUrlFor(item: VideoItem): String {
        return selectPlaybackQuality(qualitiesFor(item))?.url ?: item.videoUrl
    }

    private fun refreshPreloadWindow() {
        if (!playerManager.isInitialized) return
        val s = _state.value
        val currentIndex = s.currentPosition
        val requests = ((currentIndex - PRELOAD_BEHIND_COUNT)..(currentIndex + PRELOAD_AHEAD_COUNT))
            .mapNotNull { index ->
                val video = s.items.getOrNull(index) as? VideoItem ?: return@mapNotNull null
                VideoPreloadRequest(index = index, url = playbackUrlFor(video))
            }
        playerManager.updatePreloadWindow(currentIndex, requests)
    }

    private fun pausePlayer() {
        playerManager.pause()
        stopProgress()
    }

    private fun resumePlayer() {
        if (playingPosition >= 0) {
            playerManager.resume()
            startProgress()
        }
    }

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
        } catch (_: Exception) {
        }
    }
}
