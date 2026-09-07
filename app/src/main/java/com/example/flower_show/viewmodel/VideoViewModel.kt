package com.example.flower_show.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.data.repository.IVideoRepository
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.DeliveryContext
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.RecommendedPage
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.model.QualityMode
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem
import com.example.flower_show.model.VideoQuality
import com.example.flower_show.model.VideoQualitySelector
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.player.VideoPreloadRequest
import com.example.flower_show.util.MetricsCollector
import com.example.flower_show.util.PerformanceDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 视频首页的 ViewModel，是视频主流程的调度中心。
 *
 * 它不直接负责画 UI，而是负责：
 * - 从 Repository 分页加载首页信息流。
 * - 维护 VideoState，让 VideoScreen 根据状态重组。
 * - 接收 VideoIntent，执行播放、暂停、跳转、加载下一页等动作。
 * - 调用 VideoPlayerManager，让真正的播放器开始播放某个 URL。
 *
 * 主线阅读顺序：
 * 1. init：进入页面时初始化播放器并加载第一页。
 * 2. dispatch：看 UI 发来的动作会被转到哪个私有函数。
 * 3. loadFirstPage / loadNextPage：看数据怎么进入 state.items。
 * 4. playPosition：看某一页怎么变成正在播放的内容。
 * 5. refreshPreloadWindow：看当前视频附近的视频如何预加载。
 */
class VideoViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 数据仓库。
     *
     * ViewModel 不直接读 assets/json，而是通过 Repository 拿数据。
     * 这样 UI/业务层不需要关心数据到底来自本地文件、网络还是假数据。
     */
    private val repository: IVideoRepository = RepositoryFactory.getVideoRepository(application)

    /**
     * 私有可变状态。
     *
     * 只有 ViewModel 内部可以调用 _state.update { ... } 修改页面状态。
     */
    private val _state = MutableStateFlow(VideoState())

    /**
     * 对 UI 暴露的只读状态。
     *
     * VideoScreen 只能 collect，不能直接改状态。
     */
    val state: StateFlow<VideoState> = _state.asStateFlow()

    /**
     * 播放器管理器。
     *
     * ViewModel 负责告诉它“播放哪个 URL、暂停、倍速、切清晰度”，
     * 但真正的 ExoPlayer 创建、缓存、预加载在 VideoPlayerManager 里。
     */
    val playerManager = VideoPlayerManager(application)

    /**
     * 当前已经加载到第几页。
     *
     * 注意它不是当前播放页，而是分页加载用的页码。
     */
    private var catalogPage = 0
    private var isCatalogPositioning = false
    private var recommendedCursor: String? = null
    private var recommendedServeSessionId: String? = null
    private var followingCursor: String? = null
    private var feedRequestGeneration = 0L
    private var commentRequestGeneration = 0L
    private var eventSequence = 0L
    private val viewerFeedBinding = ViewerFeedBinding()
    private val reportedImpressionDeliveryIds = mutableSetOf<String>()
    private var lastPlayStartDeliveryId: String? = null

    /**
     * 当前正在交给播放器播放的列表位置。
     *
     * 它和 state.currentPosition 接近，但 playingPosition 更偏向播放器内部控制。
     */
    private var playingPosition = -1

    /**
     * 退到后台前播放器是否正在播放。
     *
     * 只有这个标志为 true 时，回到前台才自动恢复播放；
     * 用户在后台前主动暂停过的话保持暂停。
     */
    private var wasPlayingBeforeBackground = false

    /**
     * 周期性上报播放进度的协程任务。
     */
    private var progressJob: Job? = null

    // 下面这些字段是自动清晰度策略用的运行时数据。
    // 第一遍读视频主流程时，只需要知道：它们用来判断网络是否卡顿、要不要升/降清晰度。
    private val bufferingHistory = mutableListOf<Long>()
    private var lastPlayableBufferMs: Long = 0
    private var lastEstimatedBandwidthKbps: Int = 0
    private var stablePlaybackSinceMs: Long = 0
    private var lastAutoSwitchMs: Long = 0
    private var qualityJob: Job? = null

    /**
     * 用户手动选择的清晰度按视频 id 记忆。
     *
     * 滑过图片/图集卡时 currentQualityName 会被清空，回视频时 Manual 模式
     * 找不到名称会静默回退到最高清晰度；按 id 记忆可以恢复用户的选择。
     */
    private val manualQualityByVideoId = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "VideoViewModel"

        /**
         * 每页加载多少条信息流内容。
         */
        private const val PAGE_SIZE = 10
        private const val MaxCommentLength = 1_000

        // 自动清晰度相关阈值：今天先跳过，知道它们控制“卡顿时降清晰度、稳定时升清晰度”即可。
        private const val BUFFERING_WINDOW_MS = 30_000L
        private const val BUFFERING_MAX_COUNT = 2
        private const val BUFFERING_MAX_DURATION = 2_000L
        private const val LOW_PLAYABLE_BUFFER_MS = 1_500L
        private const val DOWNGRADE_BANDWIDTH_HEADROOM = 0.90

        private const val STABLE_UPGRADE_MS = 60_000L
        private const val UPGRADE_PLAYABLE_BUFFER_MS = 5_000L
        private const val UPGRADE_BANDWIDTH_HEADROOM = 1.35
        private const val AUTO_EVALUATION_INTERVAL_MS = 5_000L

        // 预加载让路阈值：可播缓冲低于该值暂停预加载，恢复到更高值才恢复（滞回）。
        private const val PAUSE_PRELOAD_PLAYABLE_BUFFER_MS = 1_500L
        private const val RESUME_PRELOAD_PLAYABLE_BUFFER_MS = 4_000L

        private const val COOLDOWN_MS = 30_000L

        /**
         * 预加载窗口：当前页前面预加载 1 条，后面预加载 3 条。
         *
         * 短视频滑动很快，提前准备后面几条能减少黑屏和等待。
         */
        private const val PRELOAD_BEHIND_COUNT = 1
        private const val PRELOAD_AHEAD_COUNT = 3

        /**
         * 允许用户选择的倍速。
         */
        private val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)
    }

    /**
     * 播放器事件回调。
     *
     * VideoPlayerManager 会把缓冲开始、缓冲结束、播放进度等事件发回来。
     * ViewModel 根据这些事件更新自动清晰度相关状态。
     *
     * 第一遍读主流程时，这块可以先跳读。
     */
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
                updatePreloadBudget(event.playableBufferMs)
                updateStablePlaybackClock()
                evaluateAutoDowngrade(reason = "low_buffer_or_bandwidth", bufferingDurationMs = 0L)
            }

            is PlayerCallback.PlaybackEvent.VideoSizeChanged -> {
                if (currentVideoUsesAdaptiveHls()) {
                    VideoQualitySelector.qualityNameForVideoSize(event.width, event.height)?.let { qualityName ->
                        // 写回前与 availableQualities 做名称归一化匹配：
                        // 直接写入 "720p" 这类名称时，自动升降级的 find/indexOfFirst
                        // 会因与原始 key 不一致而静默失效；匹配不到就不更新。
                        val matched = _state.value.availableQualities
                            .firstOrNull { it.name.equals(qualityName, ignoreCase = true) }
                            ?.name
                        if (matched != null) {
                            _state.update { it.copy(currentQualityName = matched) }
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    init {
        // ViewModel 创建后立即准备播放器，并加载第一页。
        // 因为 VideoScreen 默认就是首页视频流，所以不需要等用户点击再加载。
        playerManager.addCallback(qualityCallback)
        // SimpleCache 磁盘索引读取等重资源先放后台预热，
        // 主线程 initialize() 时直接复用，减少首帧前的主线程阻塞。
        viewModelScope.launch(Dispatchers.IO) {
            playerManager.warmUpAsync()
        }
        playerManager.initialize()
        _state.update { it.copy(isPlayerReady = true) }
        loadFirstPage()
    }

    /**
     * UI 和 ViewModel 之间的统一入口。
     *
     * 你可以把它想成“动作路由表”：
     * VideoScreen 发来一个 VideoIntent，dispatch 决定交给哪个函数处理。
     */
    fun dispatch(intent: VideoIntent) {
        when (intent) {
            is VideoIntent.SelectFeed -> selectFeed(intent.feed)
            is VideoIntent.BindViewer -> bindViewer(intent.userId)
            is VideoIntent.EnterHomeFeed -> enterHomeFeed()
            is VideoIntent.LoadFirstPage -> loadFirstPage()
            is VideoIntent.LoadNextPage -> loadNextPage()
            is VideoIntent.PlayPosition -> playPosition(intent.position)
            is VideoIntent.ReportImpression -> reportImpression(intent.position)
            is VideoIntent.PausePlayer -> pausePlayer()
            is VideoIntent.ResumePlayer -> resumePlayer()
            is VideoIntent.PauseForBackground -> pauseForBackground()
            is VideoIntent.ResumeFromBackground -> resumeFromBackground()
            is VideoIntent.TogglePlayPause -> playerManager.togglePlayPause()
            is VideoIntent.SeekTo -> playerManager.seekTo(intent.positionMs)
            is VideoIntent.SetPlaybackSpeed -> setPlaybackSpeed(intent.speed)
            is VideoIntent.DismissError -> _state.update { it.copy(error = null) }
            is VideoIntent.DismissToast -> _state.update { it.copy(toastMessage = null) }
            is VideoIntent.JumpToVideo -> jumpToVideo(intent.videoId)
            is VideoIntent.EnableAutoQuality -> enableAutoQuality()
            is VideoIntent.SelectManualQuality -> selectManualQuality(intent.name, intent.url)
            is VideoIntent.ReportBuffering -> onBufferingReported(intent.durationMs)
            is VideoIntent.ToggleLike -> toggleLike(intent.videoId)
            is VideoIntent.ToggleFavorite -> toggleFavorite(intent.videoId)
            is VideoIntent.OpenComments -> openComments(intent.videoId)
            VideoIntent.CloseComments -> _state.update {
                it.copy(commentSheetVideoId = null, commentDraft = "")
            }
            is VideoIntent.UpdateCommentDraft ->
                _state.update { it.copy(commentDraft = intent.text.take(MaxCommentLength)) }
            VideoIntent.SubmitComment -> submitComment()
        }
    }

    private fun selectFeed(feed: FeedKind) {
        if (_state.value.selectedFeed == feed) return
        _state.update {
            it.copy(selectedFeed = feed, commentSheetVideoId = null, commentDraft = "")
        }
        loadFirstPage()
    }

    private fun bindViewer(userId: String?) {
        if (!viewerFeedBinding.bind(userId)) return
        if (userId.isNullOrBlank() && _state.value.selectedFeed == FeedKind.Following) {
            selectFeed(FeedKind.Recommended)
        } else {
            loadFirstPage()
        }
    }

    /**
     * 设置播放倍速。
     *
     * 只允许 PLAYBACK_SPEEDS 里定义的倍速，避免 UI 传入奇怪值。
     */
    private fun setPlaybackSpeed(speed: Float) {
        val target = PLAYBACK_SPEEDS.firstOrNull { it == speed } ?: return
        playerManager.setPlaybackSpeed(target)
        _state.update { it.copy(playbackSpeed = target) }
        MetricsCollector.recordLabel("playback_speed", "${target}x")
        PerformanceDiagnostics.recordLabel("playback_speed", "${target}x")
    }

    /**
     * 用户手动选择清晰度。
     *
     * 这会把模式切到 Manual，并通知播放器切换到对应 URL。
     * 第一遍读主流程时可以先跳过。
     */
    private fun selectManualQuality(name: String, url: String) {
        val target = _state.value.availableQualities.firstOrNull { it.name == name && it.url == url }
        if (target == null) {
            _state.update { it.copy(toastMessage = "清晰度不可用") }
            MetricsCollector.recordLabel("manual_quality", "unavailable_$name")
            return
        }

        stopAutoQuality()
        playerManager.setAutoHlsVideoBitrateLimitEnabled(false)
        _state.update {
            it.copy(
                qualityMode = QualityMode.Manual,
                currentQualityName = target.name,
            )
        }
        // 按视频 id 记住手动选择，滑过图片/图集卡再回来时不会丢。
        (_state.value.items.getOrNull(_state.value.currentPosition) as? VideoItem)?.let { item ->
            manualQualityByVideoId[item.id] = target.name
        }
        playerManager.setQuality(target.name, target.url, trigger = "manual")
        refreshPreloadWindow()
        MetricsCollector.recordLabel("manual_quality", target.name)
        PerformanceDiagnostics.recordLabel("manual_quality", target.name)
        Log.d(TAG, "Manual quality: ${target.name}")
    }

    /**
     * 开启自动清晰度。
     *
     * 第一遍读主流程时可以先跳过。
     */
    private fun enableAutoQuality() {
        _state.update { it.copy(qualityMode = QualityMode.Auto) }
        resetQualityRuntimeState()
        val state = _state.value
        val item = state.items.getOrNull(state.currentPosition) as? VideoItem
        val hlsUrl = item?.hlsUrl?.trim().orEmpty()
        if (hlsUrl.isNotBlank()) {
            stopAutoQuality()
            playerManager.setAutoHlsVideoBitrateLimitEnabled(true)
            _state.update { it.copy(currentQualityName = AUTO_HLS_INITIAL_QUALITY_NAME) }
            playerManager.setQuality(AUTO_HLS_INITIAL_QUALITY_NAME, hlsUrl, trigger = "auto_hls")
            refreshPreloadWindow()
            MetricsCollector.recordLabel("auto_quality", "hls")
            PerformanceDiagnostics.recordLabel("auto_quality", "hls")
            Log.d(TAG, "Auto quality enabled with HLS")
            return
        }
        playerManager.setAutoHlsVideoBitrateLimitEnabled(false)
        evaluateAutoQuality()
        Log.d(TAG, "Auto quality enabled")
    }

    /**
     * 播放器报告一次卡顿后，尝试判断是否需要自动降清晰度。
     */
    private fun onBufferingReported(durationMs: Long) {
        stablePlaybackSinceMs = 0
        evaluateAutoDowngrade(reason = "buffering_duration", bufferingDurationMs = durationMs)
    }

    /**
     * 自动降清晰度判断。
     *
     * 逻辑大意：
     * - 只有 Auto 模式才工作。
     * - 最近卡顿次数太多、单次卡顿太久、或缓冲/带宽不足时，尝试降一级。
     *
     * 这是播放体验优化，不是视频主流程必读内容。
     */
    private fun evaluateAutoDowngrade(reason: String, bufferingDurationMs: Long) {
        val s = _state.value
        if (s.qualityMode != QualityMode.Auto) return
        if (currentVideoUsesAdaptiveHls()) return
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

    /**
     * 自动升清晰度判断。
     *
     * 当播放稳定一段时间、缓冲充足、带宽足够时，尝试升一级清晰度。
     * 第一遍读主流程时可以先跳过。
     */
    private fun evaluateAutoQuality() {
        if (_state.value.qualityMode != QualityMode.Auto) return
        if (currentVideoUsesAdaptiveHls()) return
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

    /**
     * 真正执行自动清晰度切换。
     */
    private fun applyAutoQualitySwitch(
        target: VideoQuality,
        direction: String,
        reason: String,
        showToast: Boolean,
    ) {
        _state.update { it.copy(currentQualityName = target.name) }
        playerManager.setQuality(target.name, target.url, trigger = "auto_${direction}_$reason")
        lastAutoSwitchMs = System.currentTimeMillis()
        stablePlaybackSinceMs = 0
        refreshPreloadWindow()
        MetricsCollector.recordLabel("auto_quality", "${direction}_${target.name}_$reason")
        PerformanceDiagnostics.recordLabel("auto_quality", "${direction}_${target.name}_$reason")
        Log.d(TAG, "Auto quality $direction -> ${target.name}, reason=$reason")
        if (showToast) {
            _state.update { it.copy(toastMessage = "网络波动，已自动切换到 ${target.name}") }
        }
    }

    /**
     * 清空自动清晰度策略的运行时统计。
     */
    private fun resetQualityRuntimeState() {
        bufferingHistory.clear()
        lastPlayableBufferMs = 0
        lastEstimatedBandwidthKbps = 0
        stablePlaybackSinceMs = 0
    }

    /**
     * 停止自动清晰度检查任务。
     */
    private fun stopAutoQuality() {
        qualityJob?.cancel()
        qualityJob = null
    }

    /**
     * 预加载带宽让路控制。
     *
     * 当前播放的可播缓冲低于阈值时暂停预加载（把带宽让给正在播放的视频），
     * 缓冲恢复充足后再重建预加载窗口。滞回区间避免阈值附近频繁抖动。
     */
    private var preloadPaused = false

    /**
     * 根据实时可播缓冲决定是否暂停/恢复预加载。
     */
    private fun updatePreloadBudget(playableBufferMs: Long) {
        if (!preloadPaused && playableBufferMs in 1 until PAUSE_PRELOAD_PLAYABLE_BUFFER_MS) {
            preloadPaused = true
            playerManager.pausePreloading()
            PerformanceDiagnostics.event(
                "preload_paused",
                mapOf("playableBufferMs" to playableBufferMs),
            )
        } else if (preloadPaused && playableBufferMs >= RESUME_PRELOAD_PLAYABLE_BUFFER_MS) {
            preloadPaused = false
            refreshPreloadWindow()
        }
    }

    /**
     * 更新“稳定播放已经持续多久”的计时。
     */
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

    /**
     * 只保留最近一段时间内的卡顿记录，避免历史卡顿一直影响当前判断。
     */
    private fun pruneBufferingHistory(now: Long = System.currentTimeMillis()) {
        val cutoff = now - BUFFERING_WINDOW_MS
        bufferingHistory.removeAll { it < cutoff }
    }

    /**
     * 进入普通首页视频流。
     *
     * 普通导航返回时保留当前推荐会话和播放位置；只有从搜索定位的目录
     * 模式返回，或者当前没有数据时，才重新创建首页推荐会话。
     */
    private fun enterHomeFeed() {
        val state = _state.value
        when (
            resolveHomeFeedEntry(
                hasItems = state.items.isNotEmpty(),
                isLoading = state.isLoading,
                isCatalogPositioning = isCatalogPositioning,
                hasPendingTarget = state.targetVideoId != null,
            )
        ) {
            HomeFeedEntryAction.Reload -> loadFirstPage()
            HomeFeedEntryAction.ResumeCurrent ->
                playPosition(state.currentPosition.coerceIn(0, state.items.lastIndex))
            HomeFeedEntryAction.Wait -> Unit
        }
    }

    /**
     * 加载第一页信息流。
     *
     * 这是视频主流程的数据入口：
     * 1. 创建新的推荐 feed 会话顺序。
     * 2. 把 currentPage 重置为 0。
     * 3. state.isLoading = true，让 UI 显示加载态。
     * 4. 在 IO 线程调用 repository.loadFeed(1, PAGE_SIZE)。
     * 5. 成功后把数据放进 state.items。
     * 6. 自动播放第 0 条。
     *
     * 每次加载第一页都会创建新会话；后续分页继续复用该会话。
     */
    private fun loadFirstPage() {
        val feed = _state.value.selectedFeed
        catalogPage = 0
        isCatalogPositioning = false
        recommendedCursor = null
        recommendedServeSessionId = null
        followingCursor = null
        val generation = ++feedRequestGeneration
        _state.update { it.copy(isLoading = true, error = null, targetVideoId = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val recommendedRequest = RecommendedFeedLoadRequest.freshSession()
            val result = when (feed) {
                FeedKind.Recommended -> repository.loadRecommendedFeed(
                    cursor = recommendedRequest.cursor,
                    serveSessionId = recommendedRequest.serveSessionId,
                    pageSize = PAGE_SIZE,
                    refresh = recommendedRequest.refresh,
                ).toLoadedRecommendedPage()
                FeedKind.Following ->
                    repository.loadFollowingFeed(null, PAGE_SIZE).toLoadedFollowingPage()
            }
            when (result) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (generation != feedRequestGeneration || _state.value.selectedFeed != feed) {
                        return@withContext
                    }
                    // 回到主线程更新 StateFlow，因为 UI 正在订阅这个状态。
                    _state.update {
                        it.copy(
                            items = result.data.items,
                            deliveryContexts = result.data.deliveryContexts,
                            isLoading = false,
                            hasMore = result.data.hasMore,
                            currentPosition = 0,
                            recommendedServeSessionId =
                                result.data.serveSessionId.takeIf { feed == FeedKind.Recommended },
                            recommendedNextCursor =
                                result.data.nextCursor.takeIf { feed == FeedKind.Recommended },
                        )
                    }
                    if (feed == FeedKind.Recommended) {
                        recommendedServeSessionId = result.data.serveSessionId
                        recommendedCursor = result.data.nextCursor
                        reportedImpressionDeliveryIds.clear()
                        lastPlayStartDeliveryId = null
                    } else {
                        followingCursor = result.data.nextCursor
                    }
                    if (result.data.items.isNotEmpty()) {
                        // 首页加载完成后立即播放第一条。
                        playPosition(0)
                    } else {
                        pausePlayer()
                    }
                }

                is Result.Error -> withContext(Dispatchers.Main) {
                    if (generation == feedRequestGeneration && _state.value.selectedFeed == feed) {
                        _state.update {
                            it.copy(isLoading = false, hasMore = false, error = result.message)
                        }
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    /**
     * 加载下一页信息流。
     *
     * 触发来源通常是 VideoScreen：
     * 当 VerticalPager 当前页接近列表末尾时，dispatch(VideoIntent.LoadNextPage)。
     *
     * 防重复：
     * - 正在加载时直接 return。
     * - 没有更多数据时直接 return。
     */
    private fun loadNextPage() {
        val s = _state.value
        if (s.isLoading || !s.hasMore) return
        _state.update { it.copy(isLoading = true) }
        val feed = s.selectedFeed
        val catalogMode = isCatalogPositioning
        val catalogNextPage = catalogPage + 1
        val cursor = if (feed == FeedKind.Recommended) recommendedCursor else followingCursor
        val sessionId = recommendedServeSessionId
        val generation = feedRequestGeneration
        viewModelScope.launch(Dispatchers.IO) {
            val recommendedRequest = RecommendedFeedLoadRequest.nextPage(
                cursor = cursor,
                serveSessionId = sessionId,
            )
            val result = when {
                catalogMode -> repository.loadFeed(catalogNextPage, PAGE_SIZE).toLoadedCatalogPage()
                feed == FeedKind.Recommended -> repository.loadRecommendedFeed(
                    cursor = recommendedRequest.cursor,
                    serveSessionId = recommendedRequest.serveSessionId,
                    pageSize = PAGE_SIZE,
                    refresh = recommendedRequest.refresh,
                ).toLoadedRecommendedPage()
                else -> repository.loadFollowingFeed(cursor, PAGE_SIZE).toLoadedFollowingPage()
            }
            when (result) {
                is Result.Success -> withContext(Dispatchers.Main) {
                    if (generation != feedRequestGeneration || _state.value.selectedFeed != feed) {
                        return@withContext
                    }
                    val current = _state.value
                    val merged = mergeFeedItems(
                        existingItems = current.items,
                        existingDeliveryContexts = current.deliveryContexts,
                        incomingItems = result.data.items,
                        incomingDeliveryContexts = result.data.deliveryContexts,
                    )
                    val cursorAdvanced = catalogMode ||
                        (!result.data.nextCursor.isNullOrBlank() && result.data.nextCursor != cursor)
                    val canLoadMore = result.data.hasMore &&
                        merged.appendedCount > 0 &&
                        cursorAdvanced
                    if (result.data.hasMore && !canLoadMore) {
                        Log.w(
                            TAG,
                            "Stopping pagination because the next page made no unique progress",
                        )
                    }
                    val effectiveNextCursor = result.data.nextCursor.takeIf { canLoadMore }
                    _state.update {
                        it.copy(
                            items = merged.items,
                            deliveryContexts = merged.deliveryContexts,
                            isLoading = false,
                            hasMore = canLoadMore,
                            recommendedServeSessionId = if (
                                feed == FeedKind.Recommended && !catalogMode
                            ) {
                                result.data.serveSessionId
                            } else {
                                it.recommendedServeSessionId
                            },
                            recommendedNextCursor = if (
                                feed == FeedKind.Recommended && !catalogMode
                            ) {
                                effectiveNextCursor
                            } else {
                                it.recommendedNextCursor
                            },
                        )
                    }
                    when {
                        catalogMode -> catalogPage = catalogNextPage
                        feed == FeedKind.Recommended -> {
                            recommendedServeSessionId = result.data.serveSessionId
                            recommendedCursor = effectiveNextCursor
                        }
                        else -> followingCursor = effectiveNextCursor
                    }
                    // 加载新数据后，预加载窗口也要刷新，因为后面可预加载的视频更多了。
                    refreshPreloadWindow()
                }

                is Result.Error -> withContext(Dispatchers.Main) {
                    if (generation == feedRequestGeneration && _state.value.selectedFeed == feed) {
                        _state.update { it.copy(isLoading = false, error = result.message) }
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun toggleLike(videoId: String) {
        val userId = currentUserId() ?: run {
            _state.update { it.copy(toastMessage = "请先登录后点赞") }
            return
        }
        val before = _state.value.items.filterIsInstance<VideoItem>()
            .firstOrNull { it.id == videoId } ?: return
        val requestKey = "like:$videoId"
        // 精确匹配：点赞与收藏是独立操作，互不拦截（endsWith 会让两者互相锁死）。
        if (requestKey in _state.value.interactionRequests) return
        val desired = !before.likedByViewer
        _state.update { current ->
            current.copy(
                items = current.items.updateVideo(videoId) {
                    it.copy(
                        likedByViewer = desired,
                        likes = (it.likes + if (desired) 1 else -1).coerceAtLeast(0),
                    )
                },
                interactionRequests = current.interactionRequests + requestKey,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.setLiked(videoId, userId, desired)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        items = current.items.updateVideo(videoId) {
                            it.copy(
                                likedByViewer = desired,
                                likes = result.data.likeCount,
                                comments = result.data.commentCount,
                                collections = result.data.favoriteCount,
                                shares = result.data.shareCount,
                            )
                        },
                        interactionRequests = current.interactionRequests - requestKey,
                    )
                }
                is Result.Error -> _state.update { current ->
                    current.copy(
                        // 回滚基于当前状态施加反向 delta，而不是回填操作发起时的旧快照：
                        // 请求期间可能有并发更新（收藏、评论数）落地，整体回填会覆盖它们。
                        items = current.items.updateVideo(videoId) {
                            it.copy(
                                likedByViewer = before.likedByViewer,
                                likes = (it.likes + if (desired) -1 else 1).coerceAtLeast(0),
                            )
                        },
                        interactionRequests = current.interactionRequests - requestKey,
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun toggleFavorite(videoId: String) {
        val userId = currentUserId() ?: run {
            _state.update { it.copy(toastMessage = "请先登录后收藏") }
            return
        }
        val before = _state.value.items.filterIsInstance<VideoItem>()
            .firstOrNull { it.id == videoId } ?: return
        val requestKey = "favorite:$videoId"
        // 精确匹配：点赞与收藏是独立操作，互不拦截（endsWith 会让两者互相锁死）。
        if (requestKey in _state.value.interactionRequests) return
        val desired = !before.favoritedByViewer
        _state.update { current ->
            current.copy(
                items = current.items.updateVideo(videoId) {
                    it.copy(
                        favoritedByViewer = desired,
                        collections = (it.collections + if (desired) 1 else -1).coerceAtLeast(0),
                    )
                },
                interactionRequests = current.interactionRequests + requestKey,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.setFavorited(videoId, userId, desired)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        items = current.items.updateVideo(videoId) {
                            it.copy(
                                favoritedByViewer = desired,
                                likes = result.data.likeCount,
                                comments = result.data.commentCount,
                                collections = result.data.favoriteCount,
                                shares = result.data.shareCount,
                            )
                        },
                        interactionRequests = current.interactionRequests - requestKey,
                    )
                }
                is Result.Error -> _state.update { current ->
                    current.copy(
                        // 与点赞相同：基于当前状态施加反向 delta，避免覆盖并发更新。
                        items = current.items.updateVideo(videoId) {
                            it.copy(
                                favoritedByViewer = before.favoritedByViewer,
                                collections = (it.collections + if (desired) -1 else 1)
                                    .coerceAtLeast(0),
                            )
                        },
                        interactionRequests = current.interactionRequests - requestKey,
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun openComments(videoId: String) {
        if (_state.value.items.none { it is VideoItem && it.id == videoId }) return
        val generation = ++commentRequestGeneration
        _state.update {
            it.copy(
                commentSheetVideoId = videoId,
                commentDraft = "",
                isCommentsLoading = true,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.loadComments(videoId)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        commentsByVideoId = current.commentsByVideoId + (videoId to result.data),
                        isCommentsLoading = if (
                            generation == commentRequestGeneration &&
                            current.commentSheetVideoId == videoId
                        ) {
                            false
                        } else {
                            current.isCommentsLoading
                        },
                    )
                }
                is Result.Error -> _state.update { current ->
                    if (
                        generation == commentRequestGeneration &&
                        current.commentSheetVideoId == videoId
                    ) {
                        current.copy(isCommentsLoading = false, error = result.message)
                    } else {
                        current
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun submitComment() {
        val state = _state.value
        val videoId = state.commentSheetVideoId ?: return
        val body = state.commentDraft.trim()
        if (body.isBlank() || state.isCommentSubmitting) return
        val userId = currentUserId() ?: run {
            _state.update { it.copy(toastMessage = "请先登录后评论") }
            return
        }
        _state.update { it.copy(isCommentSubmitting = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repository.createComment(videoId, userId, body)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        commentsByVideoId = current.commentsByVideoId + (
                            videoId to (
                                current.commentsByVideoId[videoId].orEmpty() + result.data
                            )
                        ),
                        items = current.items.updateVideo(videoId) {
                            it.copy(comments = it.comments + 1)
                        },
                        commentDraft = "",
                        isCommentSubmitting = false,
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(isCommentSubmitting = false, error = result.message)
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun currentUserId(): String? =
        AuthGraph.get(getApplication()).sessionManager.currentUserId()?.takeIf(String::isNotBlank)

    /** 供 UI 标记"我的评论"等用途的当前登录用户 id。 */
    val viewerUserId: String? get() = currentUserId()

    private fun reportImpression(position: Int) {
        val delivery = _state.value.deliveryContexts.getOrNull(position) ?: return
        if (!reportedImpressionDeliveryIds.add(delivery.id)) return
        reportDeliveryEvent(type = "impression", delivery = delivery)
    }

    private fun reportPlayStart(position: Int) {
        val delivery = _state.value.deliveryContexts.getOrNull(position) ?: return
        if (lastPlayStartDeliveryId == delivery.id) return
        lastPlayStartDeliveryId = delivery.id
        reportDeliveryEvent(
            type = "play_start",
            delivery = delivery,
            playbackId = UUID.randomUUID().toString(),
        )
    }

    private fun reportDeliveryEvent(
        type: String,
        delivery: DeliveryContext,
        playbackId: String? = null,
    ) {
        val event = RecommendationEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            occurredAtMs = System.currentTimeMillis(),
            exposureToken = delivery.exposureToken,
            contentId = delivery.contentId,
            playbackId = playbackId,
            sequence = ++eventSequence,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.reportEvents(listOf(event))
            if (result is Result.Error) {
                Log.w(TAG, "Failed to report $type: ${result.message}")
            }
        }
    }

    /**
     * 从搜索结果跳到指定内容。
     *
     * 流程：
     * 1. 先在当前已经加载的 items 里找。
     * 2. 如果找到了，更新 currentPosition 并播放。
     * 3. 如果没找到，就继续分页加载后面的数据，直到找到或没有更多数据。
     *
     * 这就是 MainActivity 里 "video:<id>" 路由最终触发的逻辑。
     */
    private fun jumpToVideo(videoId: String) {
        val initialState = _state.value
        if (initialState.items.isEmpty() && initialState.isLoading) {
            _state.update { it.copy(targetVideoId = videoId) }
            viewModelScope.launch {
                _state.first { state -> !state.isLoading }
                jumpToVideo(videoId)
            }
            return
        }

        val existingIndex = _state.value.items.indexOfFirst { it.matchesTarget(videoId) }
        if (existingIndex >= 0) {
            // 已加载列表里能找到，直接定位和播放。
            _state.update { it.copy(targetVideoId = null, currentPosition = existingIndex) }
            playPosition(existingIndex)
            return
        }

        // 暂时找不到时，把目标 id 记录进 state。
        // VideoScreen 会用它避免还没滚到目标页时误触发播放。
        val generation = ++feedRequestGeneration
        _state.update { it.copy(targetVideoId = videoId, isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var nextPage = 1
            val catalogItems = mutableListOf<CardItem>()
            val seenIdentities = mutableSetOf<String>()
            var found = false
            while (!found) {
                when (val result = repository.loadFeed(nextPage, PAGE_SIZE)) {
                    is Result.Success -> {
                        if (generation != feedRequestGeneration) return@launch
                        // 目录分页可能出现重复内容；目录模式下 Pager key 回落到 feedIdentity，
                        // 重复 key 会让 Compose 直接崩溃，所以按 identity 去重后再累积。
                        var addedCount = 0
                        result.data.forEach { item ->
                            if (seenIdentities.add(item.feedIdentity())) {
                                catalogItems += item
                                addedCount++
                            }
                        }
                        val index = catalogItems.indexOfFirst { it.matchesTarget(videoId) }
                        if (index >= 0) {
                            found = true
                            withContext(Dispatchers.Main) {
                                if (generation != feedRequestGeneration) return@withContext
                                _state.update {
                                    it.copy(
                                        items = catalogItems.toList(),
                                        deliveryContexts = List(catalogItems.size) { null },
                                        isLoading = false,
                                        hasMore = result.data.isNotEmpty(),
                                        currentPosition = index,
                                        targetVideoId = null,
                                    )
                                }
                                catalogPage = nextPage
                                isCatalogPositioning = true
                                lastPlayStartDeliveryId = null
                                playPosition(index)
                            }
                        } else if (result.data.isEmpty() || addedCount == 0) {
                            // 没有更多内容，或整页都是重复内容（服务端游标未前进）：
                            // 目标不存在，停止翻页，避免死循环。
                            withContext(Dispatchers.Main) {
                                if (generation != feedRequestGeneration) return@withContext
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        targetVideoId = null,
                                        error = "未找到该内容",
                                    )
                                }
                            }
                            break
                        } else {
                            nextPage++
                        }
                    }

                    is Result.Error -> {
                        withContext(Dispatchers.Main) {
                            if (generation != feedRequestGeneration) return@withContext
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    targetVideoId = null,
                                    error = result.message,
                                )
                            }
                        }
                        break
                    }

                    is Result.Loading -> break
                }
            }
        }
    }

    /**
     * 播放信息流中指定位置的内容。
     *
     * 这是视频主流程最核心的函数，建议你重点读。
     *
     * 它处理两种情况：
     * - 当前 item 是 VideoItem：播放视频 URL。
     * - 当前 item 不是 VideoItem：尝试播放图片/图集的背景音乐，没有音乐就暂停播放器。
     */
    private fun playPosition(position: Int) {
        val item = _state.value.items.getOrNull(position) ?: return
        if (item !is VideoItem) {
            // 首页可能混排图片/图集。它们没有 videoUrl，但可能有 bgMusicUrl。
            val bgMusicUrl = backgroundMusicUrlFor(item)
            _state.update {
                it.copy(
                    currentPosition = position,
                    availableQualities = emptyList(),
                    currentQualityName = null,
                )
            }
            stopProgress()
            stopAutoQuality()
            if (bgMusicUrl.isBlank()) {
                // 图片/图集没有背景音乐时，播放器停下来，避免上一条视频声音继续播放。
                playerManager.pause()
                playingPosition = -1
                MetricsCollector.recordLabel("image_card_music", "missing")
                return
            }

            resetQualityRuntimeState()
            // 有背景音乐时，用播放器播放音乐 URL。
            playerManager.play(
                videoUrl = bgMusicUrl,
                feedIndex = position,
                videoId = cardPlaybackId(item),
                qualityName = "bg_music",
            )
            playingPosition = position
            reportPlayStart(position)
            MetricsCollector.recordLabel("image_card_music", "playing")
            PerformanceDiagnostics.event(
                "image_card_music_play",
                mapOf(
                    "feedIndex" to position,
                    "itemType" to item.javaClass.simpleName,
                    "itemId" to cardPlaybackId(item),
                ),
            )
            return
        }

        // 走到这里说明当前 item 是真正的视频卡片。
        val qualities = qualitiesFor(item)
        val useAdaptiveHls = _state.value.qualityMode == QualityMode.Auto && !item.hlsUrl.isNullOrBlank()
        // Manual 模式下优先恢复该视频被记住的手动选择（滑过图片卡后
        // currentQualityName 被清空，否则会静默回退到最高清晰度）。
        val rememberedManual = manualQualityByVideoId[item.id]
        val effectiveQualityName = if (
            _state.value.qualityMode == QualityMode.Manual &&
            rememberedManual != null &&
            qualities.any { it.name == rememberedManual }
        ) {
            rememberedManual
        } else {
            _state.value.currentQualityName
        }
        val targetQuality = if (useAdaptiveHls) {
            null
        } else {
            selectPlaybackQuality(qualities, effectiveQualityName)
        }
        val playbackUrl = if (useAdaptiveHls) item.hlsUrl!!.trim() else targetQuality?.url ?: item.videoUrl
        val displayName = initialPlaybackQualityName(useAdaptiveHls, targetQuality?.name)
        playerManager.setAutoHlsVideoBitrateLimitEnabled(useAdaptiveHls)

        // 先把“当前页、清晰度选项、当前清晰度名称”写进 state。
        // UI 上的清晰度菜单、播放状态都依赖这些字段。
        _state.update {
            it.copy(
                currentPosition = position,
                availableQualities = qualities,
                currentQualityName = displayName,
            )
        }
        PerformanceDiagnostics.event(
            "feed_play_position",
            mapOf(
                "feedIndex" to position,
                "videoId" to item.id,
                "quality" to displayName,
                "qualityCount" to qualities.size,
            ),
        )
        resetQualityRuntimeState()

        if (!playerManager.isInitialized) {
            // 理论上 init 已经初始化播放器；这里是兜底：如果播放器还没准备好，就先初始化再播放。
            // initialize()/play() 都绑定主线程 Looper，必须在主线程执行；
            // 重资源（SimpleCache 索引）已由 init 的 warmUpAsync 在后台预热。
            viewModelScope.launch {
                playerManager.initialize()
                _state.update { it.copy(isPlayerReady = true) }
                playerManager.play(
                    videoUrl = playbackUrl,
                    feedIndex = position,
                    videoId = item.id,
                    qualityName = displayName,
                )
                playingPosition = position
                reportPlayStart(position)
                startProgress()
                refreshPreloadWindow()
                if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
            }
            return
        }

        // 正常路径：把视频 URL 交给 VideoPlayerManager 播放。
        playerManager.play(
            videoUrl = playbackUrl,
            feedIndex = position,
            videoId = item.id,
            qualityName = displayName,
        )
        playingPosition = position
        reportPlayStart(position)
        startProgress()
        refreshPreloadWindow()
        if (_state.value.qualityMode == QualityMode.Auto) evaluateAutoQuality()
    }

    /**
     * 非视频卡片的背景音乐地址。
     */
    private fun backgroundMusicUrlFor(item: CardItem): String {
        return when (item) {
            is ImageCardItem -> item.bgMusicUrl
            is AlbumCardItem -> item.bgMusicUrl
            else -> ""
        }
    }

    /**
     * 给图片/图集生成一个播放统计用 id。
     */
    private fun cardPlaybackId(item: CardItem): String? {
        return when (item) {
            is ImageCardItem -> "image:${item.id}"
            is AlbumCardItem -> "album:${item.id}"
            else -> null
        }
    }

    /**
     * 判断某个卡片是否是目标 id。
     *
     * 为什么同时支持 target == id 和 target == "video:$id"？
     * 因为不同来源可能传裸 id，也可能传带类型前缀的 id。
     */
    private fun CardItem.matchesTarget(target: String): Boolean {
        return when (this) {
            is VideoItem -> target == id || target == "video:$id"
            is ImageCardItem -> target == id || target == "image:$id"
            is AlbumCardItem -> target == id || target == "album:$id"
            else -> false
        }
    }

    /**
     * 从 VideoItem 中取出可用清晰度。
     */
    private fun qualitiesFor(item: VideoItem): List<VideoQuality> {
        return VideoQualitySelector.from(item.qualityUrls)
    }

    /**
     * 根据当前模式选择一个播放清晰度。
     */
    private fun selectPlaybackQuality(
        qualities: List<VideoQuality>,
        currentQualityName: String? = _state.value.currentQualityName,
    ): VideoQuality? {
        return VideoQualitySelector.chooseForMode(
            qualities = qualities,
            mode = _state.value.qualityMode,
            currentQualityName = currentQualityName,
        )
    }

    /**
     * 预加载时要使用的播放 URL。
     */
    private fun playbackUrlFor(item: VideoItem): String {
        if (_state.value.qualityMode == QualityMode.Auto && !item.hlsUrl.isNullOrBlank()) {
            return item.hlsUrl.trim()
        }
        return selectPlaybackQuality(qualitiesFor(item))?.url ?: item.videoUrl
    }

    private fun currentVideoUsesAdaptiveHls(): Boolean {
        val state = _state.value
        if (state.qualityMode != QualityMode.Auto) return false
        val item = state.items.getOrNull(state.currentPosition) as? VideoItem ?: return false
        return !item.hlsUrl.isNullOrBlank()
    }

    /**
     * 刷新视频预加载窗口。
     *
     * 当前播放第 N 条时，会取：
     * - N 前面 1 条
     * - N 后面 3 条
     *
     * 然后把这些视频 URL 交给 VideoPlayerManager 提前准备。
     */
    private fun refreshPreloadWindow() {
        if (!playerManager.isInitialized) return
        // 带宽让路期间不重建预加载窗口，等缓冲恢复后由 updatePreloadBudget 触发恢复。
        if (preloadPaused) return
        val s = _state.value
        val currentIndex = s.currentPosition
        val requests = ((currentIndex - PRELOAD_BEHIND_COUNT)..(currentIndex + PRELOAD_AHEAD_COUNT))
            .mapNotNull { index ->
                val video = s.items.getOrNull(index) as? VideoItem ?: return@mapNotNull null
                VideoPreloadRequest(index = index, url = playbackUrlFor(video))
        }
        playerManager.updatePreloadWindow(currentIndex, requests)
    }

    /**
     * 暂停播放，并停止进度上报任务。
     */
    private fun pausePlayer() {
        playerManager.pause()
        stopProgress()
    }

    /**
     * 恢复播放，并重新开始进度上报任务。
     */
    private fun resumePlayer() {
        if (playingPosition >= 0) {
            playerManager.resume()
            startProgress()
        }
    }

    /**
     * 应用退到后台时暂停播放。
     *
     * 只记录"暂停前是否正在播放"，用于回到前台时判断要不要自动恢复；
     * 用户主动暂停过的视频不会被强行续播。
     */
    private fun pauseForBackground() {
        wasPlayingBeforeBackground = playerManager.isPlaying
        pausePlayer()
    }

    /**
     * 应用回到前台时，恢复退到后台前正在播放的内容。
     */
    private fun resumeFromBackground() {
        if (wasPlayingBeforeBackground) {
            resumePlayer()
        }
        wasPlayingBeforeBackground = false
    }

    /**
     * 启动进度上报协程。
     *
     * 每 200ms 通知一次播放器上报当前进度、缓冲、带宽等信息。
     */
    private fun startProgress() {
        stopProgress()
        progressJob = viewModelScope.launch {
            while (isActive) {
                playerManager.notifyProgress()
                delay(200)
            }
        }
    }

    /**
     * 停止进度上报协程。
     */
    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * ViewModel 销毁时释放资源。
     *
     * 这里很重要：
     * - 停止协程任务
     * - 移除播放器回调
     * - 释放播放器
     * - 写入性能统计
     */
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
            PerformanceDiagnostics.flushToDisk(getApplication(), report)
        } catch (_: Exception) {
        }
    }
}

internal const val AUTO_HLS_INITIAL_QUALITY_NAME = "480p"

internal fun initialPlaybackQualityName(
    useAdaptiveHls: Boolean,
    selectedQualityName: String?,
): String? = if (useAdaptiveHls) AUTO_HLS_INITIAL_QUALITY_NAME else selectedQualityName

private data class LoadedFeedPage(
    val items: List<CardItem>,
    val deliveryContexts: List<DeliveryContext?>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val serveSessionId: String?,
)

private fun Result<RecommendedPage>.toLoadedRecommendedPage(): Result<LoadedFeedPage> = when (this) {
    is Result.Success -> Result.success(
        LoadedFeedPage(
            items = data.items.map { it.card },
            deliveryContexts = data.items.map { it.delivery },
            nextCursor = data.nextCursor,
            hasMore = data.hasMore,
            serveSessionId = data.serveSessionId,
        ),
    )
    is Result.Error -> this
    Result.Loading -> Result.Loading
}

private fun Result<CursorPage<CardItem>>.toLoadedFollowingPage(): Result<LoadedFeedPage> = when (this) {
    is Result.Success -> Result.success(
        LoadedFeedPage(
            items = data.items,
            deliveryContexts = List(data.items.size) { null },
            nextCursor = data.nextCursor,
            hasMore = data.hasMore,
            serveSessionId = null,
        ),
    )
    is Result.Error -> this
    Result.Loading -> Result.Loading
}

private fun Result<List<CardItem>>.toLoadedCatalogPage(): Result<LoadedFeedPage> = when (this) {
    is Result.Success -> Result.success(
        LoadedFeedPage(
            items = data,
            deliveryContexts = List(data.size) { null },
            nextCursor = null,
            hasMore = data.isNotEmpty(),
            serveSessionId = null,
        ),
    )
    is Result.Error -> this
    Result.Loading -> Result.Loading
}

private fun List<CardItem>.updateVideo(
    videoId: String,
    transform: (VideoItem) -> VideoItem,
): List<CardItem> = map { item ->
    if (item is VideoItem && item.id == videoId) transform(item) else item
}

internal data class RecommendedFeedLoadRequest(
    val cursor: String?,
    val serveSessionId: String?,
    val refresh: Boolean,
) {
    companion object {
        fun freshSession(): RecommendedFeedLoadRequest =
            RecommendedFeedLoadRequest(
                cursor = null,
                serveSessionId = null,
                refresh = true,
            )

        fun nextPage(
            cursor: String?,
            serveSessionId: String?,
        ): RecommendedFeedLoadRequest =
            RecommendedFeedLoadRequest(
                cursor = cursor,
                serveSessionId = serveSessionId,
                refresh = false,
            )
    }
}

internal enum class HomeFeedEntryAction {
    Reload,
    ResumeCurrent,
    Wait,
}

internal fun resolveHomeFeedEntry(
    hasItems: Boolean,
    isLoading: Boolean,
    isCatalogPositioning: Boolean,
    hasPendingTarget: Boolean,
): HomeFeedEntryAction = when {
    hasPendingTarget || isCatalogPositioning -> HomeFeedEntryAction.Reload
    hasItems -> HomeFeedEntryAction.ResumeCurrent
    isLoading -> HomeFeedEntryAction.Wait
    else -> HomeFeedEntryAction.Reload
}

internal class ViewerFeedBinding {
    private var initialized = false
    private var userId: String? = null

    fun bind(value: String?): Boolean {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty)
        if (!initialized) {
            initialized = true
            userId = normalized
            return normalized != null
        }
        if (userId == normalized) return false
        userId = normalized
        return true
    }
}

internal data class MergedFeedItems(
    val items: List<CardItem>,
    val deliveryContexts: List<DeliveryContext?>,
    val appendedCount: Int,
)

internal fun mergeFeedItems(
    existingItems: List<CardItem>,
    existingDeliveryContexts: List<DeliveryContext?>,
    incomingItems: List<CardItem>,
    incomingDeliveryContexts: List<DeliveryContext?>,
): MergedFeedItems {
    val mergedItems = existingItems.toMutableList()
    val mergedContexts = existingItems.indices
        .mapTo(mutableListOf()) { existingDeliveryContexts.getOrNull(it) }
    val identities = existingItems.mapTo(mutableSetOf(), CardItem::feedIdentity)
    var appendedCount = 0

    incomingItems.forEachIndexed { index, item ->
        if (identities.add(item.feedIdentity())) {
            mergedItems += item
            mergedContexts += incomingDeliveryContexts.getOrNull(index)
            appendedCount++
        }
    }
    return MergedFeedItems(
        items = mergedItems,
        deliveryContexts = mergedContexts,
        appendedCount = appendedCount,
    )
}

private fun CardItem.feedIdentity(): String = when (this) {
    is VideoItem -> "video:${id.ifBlank { videoUrl }}"
    is ImageCardItem -> "image:${id.ifBlank { imageUrl }}"
    is AlbumCardItem -> "album:${id.ifBlank { slides.joinToString("|") { it.mediaUrl } }}"
    CardItem.TypeVideo -> "type:video"
    CardItem.TypeImage -> "type:image"
    CardItem.TypeAlbum -> "type:album"
}
