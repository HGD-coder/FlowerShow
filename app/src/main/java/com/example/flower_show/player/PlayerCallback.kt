package com.example.flower_show.player

fun interface PlayerCallback {
    fun onEvent(event: PlaybackEvent)

    sealed class PlaybackEvent {
        data class Ready(val durationMs: Long) : PlaybackEvent()
        data class Progress(
            val positionMs: Long,
            val bufferedMs: Long,
            val bufferedPercent: Int = 0,
            val estimatedBandwidthKbps: Int = 0,
        ) : PlaybackEvent()
        data class StateChanged(val isPlaying: Boolean) : PlaybackEvent()
        data object Complete : PlaybackEvent()
        data class Error(val message: String) : PlaybackEvent()
        /** Player entered buffering state */
        data class BufferingStart(val timestampMs: Long) : PlaybackEvent()
        /** Player exited buffering state, [durationMs] is how long it was stuck */
        data class BufferingEnd(val durationMs: Long) : PlaybackEvent()
    }
}
