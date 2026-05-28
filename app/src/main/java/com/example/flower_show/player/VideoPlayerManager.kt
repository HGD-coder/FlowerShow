package com.example.flower_show.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.flower_show.util.MetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * VideoPlayerManager — ExoPlayer (Media3) wrapper with caching.
 * Single ExoPlayer instance with lazy initialization.
 */
class VideoPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var currentVideoUrl: String? = null
    private val callbacks = mutableListOf<PlayerCallback>()
    private var playStartTimeMs: Long = 0

    companion object {
        private const val TAG = "VideoPlayerManager"
    }

    fun initialize() {
        if (player != null) return
        val cache = CacheManager.getInstance(appContext)
        val upstreamFactory = DefaultHttpDataSource.Factory()
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheFactory))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                val dur = duration
                                val latency = if (playStartTimeMs > 0) System.currentTimeMillis() - playStartTimeMs else 0
                                val cached = latency < 200
                                MetricsCollector.record("video_startup|cached=$cached", latency)
                                playStartTimeMs = 0
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Ready(dur)) }
                                callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(playWhenReady)) }
                            }
                            Player.STATE_ENDED -> callbacks.forEach {
                                it.onEvent(PlayerCallback.PlaybackEvent.Complete)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val msg = "播放失败: ${error.errorCodeName} - ${error.message} | url=$currentVideoUrl"
                        Log.e(TAG, msg)
                        callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Error(msg)) }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(isPlaying)) }
                    }
                })
            }
    }

    fun play(videoUrl: String) {
        val p = player ?: run { initialize(); player } ?: return
        Log.d(TAG, "play() url=$videoUrl")
        playStartTimeMs = System.currentTimeMillis()

        if (videoUrl == currentVideoUrl && p.playbackState == Player.STATE_READY) {
            p.play()
            return
        }
        currentVideoUrl = videoUrl
        p.setMediaItem(MediaItem.fromUri(videoUrl))
        p.prepare()
        p.play()
    }

    fun pause() = player?.pause()
    fun resume() { player?.playWhenReady = true; if (player?.playbackState == Player.STATE_READY) player?.play() }
    fun togglePlayPause() { if (player?.isPlaying == true) pause() else resume() }
    fun seekTo(positionMs: Long) = player?.seekTo(positionMs)

    val currentPosition: Long get() = player?.currentPosition ?: 0
    val duration: Long get() = player?.duration ?: 0
    val isPlaying: Boolean get() = player?.isPlaying == true
    val isInitialized: Boolean get() = player != null
    fun getPlayer(): ExoPlayer? = player

    fun addCallback(cb: PlayerCallback) { callbacks.add(cb) }
    fun removeCallback(cb: PlayerCallback) { callbacks.remove(cb) }
    fun clearCallbacks() { callbacks.clear() }

    fun notifyProgress() {
        val p = player ?: return
        if (p.isPlaying) {
            val bufferedPercent = if (p.duration > 0) (p.bufferedPosition.toFloat() / p.duration * 100).toInt() else 0
            val evt = PlayerCallback.PlaybackEvent.Progress(p.currentPosition, p.bufferedPosition, bufferedPercent)
            callbacks.forEach { it.onEvent(evt) }
        }
    }

    /**
     * Prefetch a video URL into cache for faster playback on next access.
     * Uses a fire-and-forget cache-aware read to warm the LRU cache.
     */
    fun prefetchUrl(videoUrl: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val cache = CacheManager.getInstance(appContext)
                val dataSourceFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                val dataSource = dataSourceFactory.createDataSource()
                val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(videoUrl))
                dataSource.open(dataSpec)
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                var bytesRead: Int
                // Read first 1 MB to warm the beginning of the video
                while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != -1 && totalRead < 1_048_576L) {
                    totalRead += bytesRead
                }
                dataSource.close()
                Log.d(TAG, "Prefetch complete: $totalRead bytes from $videoUrl")
            } catch (e: Exception) {
                Log.w(TAG, "Prefetch failed: ${e.message}")
            }
        }
    }

    /**
     * Switch video quality by replacing the media URL while preserving playback position.
     */
    fun setQuality(qualityName: String, qualityUrl: String) {
        val p = player ?: return
        if (qualityUrl == currentVideoUrl) return
        val pos = p.currentPosition
        val switchStartMs = System.currentTimeMillis()
        currentVideoUrl = qualityUrl
        p.setMediaItem(MediaItem.fromUri(qualityUrl))
        p.prepare()
        p.play()
        p.seekTo(pos)
        val latency = System.currentTimeMillis() - switchStartMs
        MetricsCollector.record("quality_switch", latency)
        Log.d(TAG, "Switched quality to $qualityName (${latency}ms)")
    }

    fun release() {
        clearCallbacks()
        player?.release()
        player = null
        currentVideoUrl = null
    }
}
