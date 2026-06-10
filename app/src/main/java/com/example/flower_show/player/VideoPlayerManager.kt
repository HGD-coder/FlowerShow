package com.example.flower_show.player

import android.content.Context
import android.os.Looper
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
import com.example.flower_show.util.PerformanceTrace

/**
 * VideoPlayerManager — ExoPlayer (Media3) wrapper with caching.
 * Single ExoPlayer instance with lazy initialization.
 */
class VideoPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var preloadController: FeedPreloadController? = null
    private var currentVideoUrl: String? = null
    private val callbacks = mutableListOf<PlayerCallback>()
    private var playStartTimeMs: Long = 0
    private var bufferingStartMs: Long = 0
    private var currentVideoWidth: Int = 0
    private var currentVideoHeight: Int = 0
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var firstFrameStartMs: Long = 0
    private var firstFrameTraceCookie: Int? = null
    private var bufferingTraceCookie: Int? = null
    private var qualitySwitchStartMs: Long = 0
    private var qualitySwitchTraceCookie: Int? = null

    companion object {
        private const val TAG = "VideoPlayerManager"
    }

    fun initialize() {
        if (player != null) return
        val cacheFactory = CacheManager.createCacheDataSourceFactory(appContext)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(cacheFactory)
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
        val trackSelector = DefaultTrackSelector(appContext)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext).build()
        this.bandwidthMeter = bandwidthMeter
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val loadControlSettings = ShortVideoLoadControl.defaultSettings()
        val loadControl = ShortVideoLoadControl.create(allocator, loadControlSettings)
        ShortVideoLoadControl.recordMetrics(loadControlSettings)
        val playbackLooper = Looper.getMainLooper()
        player = ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setLooper(playbackLooper)
            .build().apply {
                Log.d(
                    TAG,
                    "LoadControl profile=${loadControlSettings.profile.id} " +
                        "min=${loadControlSettings.minBufferMs}ms " +
                        "max=${loadControlSettings.maxBufferMs}ms " +
                        "start=${loadControlSettings.bufferForPlaybackMs}ms " +
                        "rebuffer=${loadControlSettings.bufferForPlaybackAfterRebufferMs}ms " +
                        "back=${loadControlSettings.backBufferMs}ms",
                )
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                // If we just exited buffering, report duration
                                if (bufferingStartMs > 0) {
                                    val dur = System.currentTimeMillis() - bufferingStartMs
                                    bufferingStartMs = 0
                                    endBufferingTrace()
                                    MetricsCollector.record("video_buffering", dur)
                                    callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.BufferingEnd(dur)) }
                                }
                                val dur = duration
                                val latency = if (playStartTimeMs > 0) System.currentTimeMillis() - playStartTimeMs else 0
                                val cached = latency < 200
                                MetricsCollector.record("video_startup|cached=$cached", latency)
                                playStartTimeMs = 0
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Ready(dur)) }
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(playWhenReady)) }
                            }
                            Player.STATE_BUFFERING -> {
                                if (bufferingStartMs == 0L) {
                                    bufferingStartMs = System.currentTimeMillis()
                                    beginBufferingTrace()
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
                        endFirstFrameTrace()
                        endBufferingTrace()
                        endQualitySwitchTrace()
                        callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Error(msg)) }
                    }

                    override fun onRenderedFirstFrame() {
                        val firstFrameLatency = if (firstFrameStartMs > 0) {
                            System.currentTimeMillis() - firstFrameStartMs
                        } else {
                            0
                        }
                        if (firstFrameLatency > 0) {
                            MetricsCollector.record("video_first_frame", firstFrameLatency)
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
        preloadController = FeedPreloadController(
            mediaSourceFactory = mediaSourceFactory,
            trackSelector = trackSelector,
            bandwidthMeter = bandwidthMeter,
            renderersFactory = renderersFactory,
            allocator = allocator,
            preloadLooper = playbackLooper,
        )
    }

    fun play(videoUrl: String, feedIndex: Int = C.INDEX_UNSET) {
        val p = player ?: run { initialize(); player } ?: return
        Log.d(TAG, "play() url=$videoUrl")
        playStartTimeMs = System.currentTimeMillis()

        if (videoUrl == currentVideoUrl && p.playbackState == Player.STATE_READY) {
            p.play()
            return
        }
        currentVideoUrl = videoUrl
        beginFirstFrameTrace()
        val preloadedMediaSource = preloadController?.getMediaSource(videoUrl)
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

    val currentPosition: Long get() = player?.currentPosition ?: 0
    val duration: Long get() = player?.duration ?: 0
    val isPlaying: Boolean get() = player?.isPlaying == true
    val isInitialized: Boolean get() = player != null
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
        }
    }

    fun updatePreloadWindow(currentIndex: Int, requests: List<VideoPreloadRequest>) {
        if (player == null) initialize()
        preloadController?.updateWindow(currentIndex, requests)
    }

    /**
     * Switch video quality by replacing the media URL while preserving playback position.
     */
    fun setQuality(qualityName: String, qualityUrl: String) {
        val p = player ?: return
        beginQualitySwitchTrace()
        if (qualityUrl == currentVideoUrl) {
            MetricsCollector.record("quality_switch|noop=true", 0)
            endQualitySwitchTrace()
            return
        }
        val pos = p.currentPosition
        val shouldPlay = p.playWhenReady || p.isPlaying
        currentVideoUrl = qualityUrl
        beginFirstFrameTrace()
        val preloadedMediaSource = preloadController?.getMediaSource(qualityUrl)
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
        currentVideoWidth = 0
        currentVideoHeight = 0
        bandwidthMeter = null
    }

    private fun beginFirstFrameTrace() {
        endFirstFrameTrace()
        firstFrameStartMs = System.currentTimeMillis()
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
        endQualitySwitchTrace()
        qualitySwitchStartMs = System.currentTimeMillis()
        qualitySwitchTraceCookie = PerformanceTrace.beginAsyncSection(PerformanceTrace.QUALITY_SWITCH)
    }

    private fun endQualitySwitchTrace() {
        val cookie = qualitySwitchTraceCookie
        if (cookie != null && qualitySwitchStartMs > 0) {
            MetricsCollector.record("quality_switch", System.currentTimeMillis() - qualitySwitchStartMs)
        }
        PerformanceTrace.endAsyncSection(PerformanceTrace.QUALITY_SWITCH, cookie)
        qualitySwitchTraceCookie = null
        qualitySwitchStartMs = 0
    }
}
