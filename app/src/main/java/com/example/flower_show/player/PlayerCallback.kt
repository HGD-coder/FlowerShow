package com.example.flower_show.player

/**
 * PlayerCallback — Functional interface for playback events.
 * SAM conversion allows lambda usage: playerManager.addCallback { event -> ... }
 */
fun interface PlayerCallback {
    fun onEvent(event: PlaybackEvent)

    sealed class PlaybackEvent {
        data class Ready(val durationMs: Long) : PlaybackEvent()
        data class Progress(
            val positionMs: Long,
            val bufferedMs: Long,
            val bufferedPercent: Int = 0,
        ) : PlaybackEvent()
        data class StateChanged(val isPlaying: Boolean) : PlaybackEvent()
        data object Complete : PlaybackEvent()
        data class Error(val message: String) : PlaybackEvent()
    }
}
