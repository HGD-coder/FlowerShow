package com.example.flower_show.player

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.flower_show.util.MetricsCollector
import com.example.flower_show.util.PerformanceDiagnostics
import com.example.flower_show.util.PerformanceExperimentConfig
import com.example.flower_show.util.PerformanceTrace
import kotlin.math.abs

/**
 * VideoPlayerManager — ExoPlayer (Media3) wrapper with caching.
 * Single ExoPlayer instance with lazy initialization.
 */
class VideoPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var preloadController: FeedPreloadController? = null
    private var currentVideoUrl: String? = null
    private var currentVideoId: String? = null
    private var currentFeedIndex: Int = C.INDEX_UNSET
    private var currentQualityName: String? = null
    private val callbacks = mutableListOf<PlayerCallback>()
    private var playStartTimeMs: Long = 0
    private var bufferingStartMs: Long = 0
    private var bufferingCountForCurrentVideo: Int = 0
    private var currentVideoWidth: Int = 0
    private var currentVideoHeight: Int = 0
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var firstFrameStartMs: Long = 0
    private var firstFrameTraceCookie: Int? = null
    private var firstFrameReason: String = "play"
    private var firstFrameSource: String = "unknown"
    private var bufferingTraceCookie: Int? = null
    private var qualitySwitchStartMs: Long = 0
    private var qualitySwitchTraceCookie: Int? = null
    private var qualitySwitchFromName: String? = null
    private var qualitySwitchToName: String? = null
    private var qualitySwitchTrigger: String = "manual"
    private var qualitySwitchPositionMs: Long = 0
    private var qualitySwitchSource: String = "direct"
    private var lastHealthLogMs: Long = 0
    @Volatile private var playbackSpeed: Float = 1f

    companion object {
        private const val TAG = "VideoPlayerManager"
        private const val PLAYBACK_HEALTH_INTERVAL_MS = 5_000L
        private const val LOW_BUFFER_HEALTH_INTERVAL_MS = 1_000L
        private const val LOW_PLAYABLE_BUFFER_MS = 1_500L
    }

    fun initialize() {
        if (player != null) return
        val profile = PerformanceExperimentConfig.current
        PerformanceDiagnostics.event(
            "player_experiment_profile",
            mapOf(
                "profile" to profile.id,
                "cache" to profile.enableCache,
                "preload" to profile.enablePreload,
                "shortLoadControl" to profile.enableShortVideoLoadControl,
            ),
        )
        val dataSourceFactory = CacheManager.createDataSourceFactory(appContext)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
        val trackSelector = DefaultTrackSelector(appContext)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext).build()
        this.bandwidthMeter = bandwidthMeter
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val loadControlSettings = if (profile.enableShortVideoLoadControl) {
            ShortVideoLoadControl.defaultSettings()
        } else {
            null
        }
        val loadControl = loadControlSettings?.let { ShortVideoLoadControl.create(allocator, it) }
        if (loadControlSettings != null) {
            ShortVideoLoadControl.recordMetrics(loadControlSettings)
        } else {
            MetricsCollector.recordLabel("load_control_profile", "media3_default")
            PerformanceDiagnostics.event(
                "load_control_profile",
                mapOf(
                    "profile" to "media3_default",
                    "experiment" to profile.id,
                ),
            )
        }
        val playbackLooper = Looper.getMainLooper()
        val playerBuilder = ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLooper(playbackLooper)
        if (loadControl != null) {
            playerBuilder.setLoadControl(loadControl)
        }
        player = playerBuilder
            .build().apply {
                this.repeatMode = Player.REPEAT_MODE_ONE
                this.setPlaybackSpeed(playbackSpeed)
                if (loadControlSettings != null) {
                    Log.d(
                        TAG,
                        "LoadControl profile=${loadControlSettings.profile.id} " +
                            "min=${loadControlSettings.minBufferMs}ms " +
                            "max=${loadControlSettings.maxBufferMs}ms " +
                            "start=${loadControlSettings.bufferForPlaybackMs}ms " +
                            "rebuffer=${loadControlSettings.bufferForPlaybackAfterRebufferMs}ms " +
                            "back=${loadControlSettings.backBufferMs}ms",
                    )
                } else {
                    Log.d(TAG, "LoadControl profile=media3_default")
                }
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                // If we just exited buffering, report duration
                                if (bufferingStartMs > 0) {
                                    val dur = SystemClock.elapsedRealtime() - bufferingStartMs
                                    bufferingStartMs = 0
                                    endBufferingTrace()
                                    MetricsCollector.record("video_buffering", dur)
                                    PerformanceDiagnostics.recordDuration(
                                        "video_buffering",
                                        dur,
                                        playbackAttributes(
                                            "bufferingCount" to bufferingCountForCurrentVideo,
                                        ),
                                    )
                                    callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.BufferingEnd(dur)) }
                                }
                                val dur = duration
                                val latency = if (playStartTimeMs > 0) {
                                    SystemClock.elapsedRealtime() - playStartTimeMs
                                } else {
                                    0
                                }
                                val cached = latency < 200
                                MetricsCollector.record("video_startup|cached=$cached", latency)
                                if (latency > 0) {
                                    PerformanceDiagnostics.recordDuration(
                                        "video_ready",
                                        latency,
                                        playbackAttributes(
                                            "durationMs" to dur,
                                            "cachedHeuristic" to cached,
                                            "state" to "ready",
                                        ),
                                    )
                                }
                                playStartTimeMs = 0
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Ready(dur)) }
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(playWhenReady)) }
                            }
                            Player.STATE_BUFFERING -> {
                                if (bufferingStartMs == 0L) {
                                    bufferingStartMs = SystemClock.elapsedRealtime()
                                    bufferingCountForCurrentVideo += 1
                                    beginBufferingTrace()
                                    PerformanceDiagnostics.event(
                                        "video_buffering_start",
                                        playbackAttributes(
                                            "bufferingCount" to bufferingCountForCurrentVideo,
                                        ),
                                    )
                                    callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.BufferingStart(bufferingStartMs)) }
                                }
                            }
                            Player.STATE_ENDED -> callbacks.forEach {
                                it.onEvent(PlayerCallback.PlaybackEvent.Complete)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val msg = "播放失败: ${error.errorCodeName} - ${error.message} | url=$currentVideoUrl"
                        Log.e(TAG, msg)
                        PerformanceDiagnostics.event(
                            "video_error",
                            playbackAttributes(
                                "errorCode" to error.errorCodeName,
                                "message" to error.message,
                            ),
                        )
                        endFirstFrameTrace()
                        endBufferingTrace()
                        endQualitySwitchTrace()
                        callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Error(msg)) }
                    }

                    override fun onRenderedFirstFrame() {
                        val firstFrameLatency = if (firstFrameStartMs > 0) {
                            SystemClock.elapsedRealtime() - firstFrameStartMs
                        } else {
                            0
                        }
                        if (firstFrameLatency > 0) {
                            MetricsCollector.record("video_first_frame", firstFrameLatency)
                            PerformanceDiagnostics.recordDuration(
                                "video_first_frame",
                                firstFrameLatency,
                                playbackAttributes(
                                    "reason" to firstFrameReason,
                                    "source" to firstFrameSource,
                                    "bufferingCount" to bufferingCountForCurrentVideo,
                                ),
                            )
                        }
                        endFirstFrameTrace()
                        endQualitySwitchTrace()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(isPlaying)) }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        currentVideoWidth = videoSize.width
                        currentVideoHeight = videoSize.height
                        PerformanceDiagnostics.event(
                            "video_size_changed",
                            playbackAttributes(
                                "width" to videoSize.width,
                                "height" to videoSize.height,
                            ),
                        )
                        callbacks.forEach {
                            it.onEvent(
                                PlayerCallback.PlaybackEvent.VideoSizeChanged(
                                    width = videoSize.width,
                                    height = videoSize.height,
                                ),
                            )
                        }
                    }
                })
            }
        preloadController = if (profile.enablePreload) {
            FeedPreloadController(
                mediaSourceFactory = mediaSourceFactory,
                trackSelector = trackSelector,
                bandwidthMeter = bandwidthMeter,
                renderersFactory = renderersFactory,
                allocator = allocator,
                preloadLooper = playbackLooper,
            )
        } else {
            PerformanceDiagnostics.event(
                "preload_disabled",
                mapOf("profile" to profile.id),
            )
            null
        }
    }

    fun play(
        videoUrl: String,
        feedIndex: Int = C.INDEX_UNSET,
        videoId: String? = null,
        qualityName: String? = null,
    ) {
        val p = player ?: run { initialize(); player } ?: return
        Log.d(TAG, "play() url=$videoUrl")
        playStartTimeMs = SystemClock.elapsedRealtime()
        currentVideoId = videoId ?: currentVideoId
        currentFeedIndex = feedIndex
        currentQualityName = qualityName ?: currentQualityName

        if (videoUrl == currentVideoUrl && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED) {
            PerformanceDiagnostics.event(
                "video_resume_current",
                playbackAttributes(
                    "playbackState" to p.playbackState,
                ),
            )
            p.playWhenReady = true
            p.play()
            return
        }
        currentVideoUrl = videoUrl
        currentVideoId = videoId
        currentFeedIndex = feedIndex
        currentQualityName = qualityName
        bufferingCountForCurrentVideo = 0
        lastHealthLogMs = 0
        val preloadedMediaSource = preloadController?.getMediaSource(videoUrl)
        val source = if (preloadedMediaSource != null) "preload_hit" else "direct"
        beginFirstFrameTrace(reason = "play", source = source)
        PerformanceDiagnostics.event(
            "video_play_request",
            playbackAttributes(
                "source" to source,
                "urlHash" to Integer.toHexString(videoUrl.hashCode()),
            ),
        )
        if (preloadedMediaSource != null) {
            p.setMediaSource(preloadedMediaSource)
            MetricsCollector.recordLabel("preload_playback_source", "hit")
        } else {
            p.setMediaItem(MediaItem.fromUri(videoUrl))
            MetricsCollector.recordLabel("preload_playback_source", "miss")
        }
        p.prepare()
        p.play()
        if (feedIndex != C.INDEX_UNSET) {
            preloadController?.onPlaybackStarted(feedIndex, videoUrl)
        }
    }

    fun pause() = player?.pause()
    fun resume() { player?.playWhenReady = true; if (player?.playbackState == Player.STATE_READY) player?.play() }
    fun togglePlayPause() { if (player?.isPlaying == true) pause() else resume() }
    fun seekTo(positionMs: Long) = player?.seekTo(positionMs)
    fun setPlaybackSpeed(speed: Float) {
        val normalizedSpeed = speed.coerceIn(0.25f, 4f)
        playbackSpeed = normalizedSpeed
        player?.setPlaybackSpeed(normalizedSpeed)
    }

    val currentPosition: Long get() = player?.currentPosition ?: 0
    val duration: Long get() = player?.duration ?: 0
    val isPlaying: Boolean get() = player?.isPlaying == true
    val isInitialized: Boolean get() = player != null
    val currentPlaybackSpeed: Float get() = playbackSpeed
    val isCurrentVideoLandscape: Boolean get() = currentVideoWidth > currentVideoHeight && currentVideoHeight > 0
    fun getPlayer(): ExoPlayer? = player

    fun addCallback(cb: PlayerCallback) { callbacks.add(cb) }
    fun removeCallback(cb: PlayerCallback) { callbacks.remove(cb) }
    fun clearCallbacks() { callbacks.clear() }

    fun notifyProgress() {
        val p = player ?: return
        if (p.isPlaying) {
            val buffered = p.bufferedPosition
            val playableBufferMs = (buffered - p.currentPosition).coerceAtLeast(0L)
            val bitrateEstimate = bandwidthMeter?.bitrateEstimate ?: 0L
            val estBwKbps = if (bitrateEstimate > 0L) {
                (bitrateEstimate / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                0
            }
            val bufferedPercent = if (p.duration > 0) (buffered.toFloat() / p.duration * 100).toInt() else 0
            val evt = PlayerCallback.PlaybackEvent.Progress(
                positionMs = p.currentPosition,
                bufferedMs = buffered,
                bufferedPercent = bufferedPercent,
                estimatedBandwidthKbps = estBwKbps,
                playableBufferMs = playableBufferMs,
            )
            callbacks.forEach { it.onEvent(evt) }
            maybeReportPlaybackHealth(evt)
        }
    }

    fun updatePreloadWindow(currentIndex: Int, requests: List<VideoPreloadRequest>) {
        if (player == null) initialize()
        if (preloadController == null) {
            PerformanceDiagnostics.event(
                "preload_window_skipped",
                mapOf(
                    "currentIndex" to currentIndex,
                    "requestedSize" to requests.size,
                    "profile" to PerformanceExperimentConfig.current.id,
                ),
            )
            return
        }
        preloadController?.updateWindow(currentIndex, requests)
    }

    /**
     * Switch video quality by replacing the media URL while preserving playback position.
     */
    fun setQuality(qualityName: String, qualityUrl: String, trigger: String = "manual") {
        val p = player ?: return
        if (qualityUrl == currentVideoUrl) {
            MetricsCollector.record("quality_switch|noop=true", 0)
            PerformanceDiagnostics.event(
                "quality_switch_noop",
                playbackAttributes(
                    "targetQuality" to qualityName,
                    "trigger" to trigger,
                ),
            )
            return
        }
        val pos = p.currentPosition
        val shouldPlay = p.playWhenReady || p.isPlaying
        endQualitySwitchTrace()
        qualitySwitchFromName = currentQualityName
        qualitySwitchToName = qualityName
        qualitySwitchTrigger = trigger
        qualitySwitchPositionMs = pos
        beginQualitySwitchTrace()
        currentVideoUrl = qualityUrl
        currentQualityName = qualityName
        val preloadedMediaSource = preloadController?.getMediaSource(qualityUrl)
        val source = if (preloadedMediaSource != null) "preload_hit" else "direct"
        qualitySwitchSource = source
        beginFirstFrameTrace(reason = "quality_switch", source = source)
        PerformanceDiagnostics.event(
            "quality_switch_start",
            playbackAttributes(
                "fromQuality" to qualitySwitchFromName,
                "toQuality" to qualitySwitchToName,
                "trigger" to trigger,
                "source" to source,
                "positionMs" to pos,
            ),
        )
        if (preloadedMediaSource != null) {
            p.setMediaSource(preloadedMediaSource, pos)
            MetricsCollector.recordLabel("quality_switch_source", "preload_hit")
        } else {
            p.setMediaItem(MediaItem.fromUri(qualityUrl), pos)
            MetricsCollector.recordLabel("quality_switch_source", "direct")
        }
        p.prepare()
        p.playWhenReady = shouldPlay
        if (shouldPlay) p.play()
        Log.d(TAG, "Switching quality to $qualityName")
    }

    fun release() {
        endFirstFrameTrace()
        endBufferingTrace()
        endQualitySwitchTrace()
        preloadController?.release()
        preloadController = null
        clearCallbacks()
        player?.release()
        player = null
        currentVideoUrl = null
        currentVideoId = null
        currentFeedIndex = C.INDEX_UNSET
        currentQualityName = null
        currentVideoWidth = 0
        currentVideoHeight = 0
        bandwidthMeter = null
    }

    private fun maybeReportPlaybackHealth(event: PlayerCallback.PlaybackEvent.Progress) {
        val now = SystemClock.elapsedRealtime()
        val isLowBuffer = event.playableBufferMs in 1 until LOW_PLAYABLE_BUFFER_MS
        val interval = if (isLowBuffer) {
            LOW_BUFFER_HEALTH_INTERVAL_MS
        } else {
            PLAYBACK_HEALTH_INTERVAL_MS
        }
        if (now - lastHealthLogMs < interval) return

        lastHealthLogMs = now
        PerformanceDiagnostics.event(
            "video_playback_health",
            playbackAttributes(
                "positionMs" to event.positionMs,
                "bufferedPositionMs" to event.bufferedMs,
                "bufferedPercent" to event.bufferedPercent,
                "playableBufferMs" to event.playableBufferMs,
                "bandwidthKbps" to event.estimatedBandwidthKbps,
                "lowBuffer" to isLowBuffer,
            ),
        )
    }

    private fun playbackAttributes(vararg attributes: Pair<String, Any?>): Map<String, Any?> {
        return buildMap {
            put("videoId", currentVideoId)
            if (currentFeedIndex != C.INDEX_UNSET) {
                put("feedIndex", currentFeedIndex)
            }
            put("quality", currentQualityName)
            currentVideoUrl?.let { put("urlHash", Integer.toHexString(it.hashCode())) }
            attributes.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun beginFirstFrameTrace(reason: String, source: String) {
        endFirstFrameTrace()
        firstFrameReason = reason
        firstFrameSource = source
        firstFrameStartMs = SystemClock.elapsedRealtime()
        firstFrameTraceCookie = PerformanceTrace.beginAsyncSection(PerformanceTrace.VIDEO_FIRST_FRAME)
    }

    private fun endFirstFrameTrace() {
        PerformanceTrace.endAsyncSection(PerformanceTrace.VIDEO_FIRST_FRAME, firstFrameTraceCookie)
        firstFrameTraceCookie = null
        firstFrameStartMs = 0
    }

    private fun beginBufferingTrace() {
        if (bufferingTraceCookie == null) {
            bufferingTraceCookie = PerformanceTrace.beginAsyncSection(PerformanceTrace.VIDEO_BUFFERING)
        }
    }

    private fun endBufferingTrace() {
        PerformanceTrace.endAsyncSection(PerformanceTrace.VIDEO_BUFFERING, bufferingTraceCookie)
        bufferingTraceCookie = null
    }

    private fun beginQualitySwitchTrace() {
        qualitySwitchStartMs = SystemClock.elapsedRealtime()
        qualitySwitchTraceCookie = PerformanceTrace.beginAsyncSection(PerformanceTrace.QUALITY_SWITCH)
    }

    private fun endQualitySwitchTrace() {
        val cookie = qualitySwitchTraceCookie
        if (cookie != null && qualitySwitchStartMs > 0) {
            val durationMs = SystemClock.elapsedRealtime() - qualitySwitchStartMs
            val currentPositionMs = player?.currentPosition ?: 0L
            MetricsCollector.record("quality_switch", durationMs)
            PerformanceDiagnostics.recordDuration(
                "quality_switch",
                durationMs,
                playbackAttributes(
                    "fromQuality" to qualitySwitchFromName,
                    "toQuality" to qualitySwitchToName,
                    "trigger" to qualitySwitchTrigger,
                    "source" to qualitySwitchSource,
                    "requestedPositionMs" to qualitySwitchPositionMs,
                    "currentPositionMs" to currentPositionMs,
                    "positionDeltaMs" to abs(currentPositionMs - qualitySwitchPositionMs),
                ),
            )
        }
        PerformanceTrace.endAsyncSection(PerformanceTrace.QUALITY_SWITCH, cookie)
        qualitySwitchTraceCookie = null
        qualitySwitchStartMs = 0
        qualitySwitchFromName = null
        qualitySwitchToName = null
        qualitySwitchPositionMs = 0
        qualitySwitchSource = "direct"
        qualitySwitchTrigger = "manual"
    }
}
