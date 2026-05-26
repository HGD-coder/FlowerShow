package com.example.flower_show.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * VideoPlayerManager — ExoPlayer (Media3) wrapper.
 * Single ExoPlayer instance with lazy initialization.
 */
class VideoPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var currentVideoUrl: String? = null
    private val callbacks = mutableListOf<PlayerCallback>()

    companion object {
        private const val TAG = "VideoPlayerManager"
    }

    fun initialize() {
        if (player != null) return
        player = ExoPlayer.Builder(appContext).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            val dur = duration
                            callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.Ready(dur)) }
                            callbacks.forEach { it.onEvent(PlayerCallback.PlaybackEvent.StateChanged(playWhenReady)) }
                        }
                        Player.STATE_ENDED -> callbacks.forEach {
                            it.onEvent(PlayerCallback.PlaybackEvent.Complete)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val msg = "播放失败: ${error.errorCodeName} - ${error.message}"
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
     * Switch video quality by replacing the media URL while preserving playback position.
     */
    fun setQuality(qualityName: String, qualityUrl: String) {
        val p = player ?: return
        if (qualityUrl == currentVideoUrl) return
        val pos = p.currentPosition
        currentVideoUrl = qualityUrl
        p.setMediaItem(MediaItem.fromUri(qualityUrl))
        p.prepare()
        p.play()
        p.seekTo(pos)
        Log.d(TAG, "Switched quality to $qualityName")
    }

    fun release() {
        clearCallbacks()
        player?.release()
        player = null
        currentVideoUrl = null
    }
}
