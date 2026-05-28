package com.example.flower_show.viewmodel

/**
 * VideoIntent — All user actions the ViewModel can handle / 用户所有可能的操作
 *
 * MVI: UI sends Intents to ViewModel. ViewModel reduces (oldState, intent) → newState.
 * MVI：UI 发 Intent 给 ViewModel，ViewModel 根据 Intent 生成新状态。
 */
sealed interface VideoIntent {
    /** Load first page of videos / 加载首页 */
    data object LoadFirstPage : VideoIntent

    /** Load next page (triggered by scroll) / 加载下一页（滑动触发） */
    data object LoadNextPage : VideoIntent

    /** Play video at given position / 播放指定位置 */
    data class PlayPosition(val position: Int) : VideoIntent

    /** Pause playback / 暂停播放 */
    data object PausePlayer : VideoIntent

    /** Resume playback / 恢复播放 */
    data object ResumePlayer : VideoIntent

    /** Toggle play/pause / 切换播放/暂停 */
    data object TogglePlayPause : VideoIntent

    /** Seek to position in milliseconds / 跳转到指定进度 */
    data class SeekTo(val positionMs: Long) : VideoIntent

    /** Error dismissed by user / 用户关闭错误 */
    data object DismissError : VideoIntent

    /** Jump to video by ID, loading pages until found / 跳转到指定视频，未找到则持续分页加载 */
    data class JumpToVideo(val videoId: String) : VideoIntent

    /** Switch video quality / 切换画质 */
    data class SetQuality(val qualityName: String, val qualityUrl: String) : VideoIntent
}
