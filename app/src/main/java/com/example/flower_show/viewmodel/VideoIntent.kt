package com.example.flower_show.viewmodel

sealed interface VideoIntent {
    data object LoadFirstPage : VideoIntent
    data object LoadNextPage : VideoIntent
    data class PlayPosition(val position: Int) : VideoIntent
    data object PausePlayer : VideoIntent
    data object ResumePlayer : VideoIntent
    data object TogglePlayPause : VideoIntent
    data class SeekTo(val positionMs: Long) : VideoIntent
    data object DismissError : VideoIntent
    data class JumpToVideo(val videoId: String) : VideoIntent

    // ── Quality / 画质 ──
    /** User selected "Auto" — enable adaptive quality */
    data object EnableAutoQuality : VideoIntent
    /** User manually picked a quality */
    data class SelectManualQuality(val name: String, val url: String) : VideoIntent
    /** Player reported buffering — may trigger auto downgrade */
    data class ReportBuffering(val durationMs: Long) : VideoIntent
    /** Dismiss toast message */
    data object DismissToast : VideoIntent
}
