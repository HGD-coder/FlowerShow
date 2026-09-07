package com.example.flower_show.viewmodel

/**
 * 视频页能处理的所有“动作”。
 *
 * 这个项目的视频页接近 MVI 写法：
 * - UI 不直接调用 ViewModel 的很多私有函数。
 * - UI 统一 dispatch(VideoIntent.xxx)。
 * - ViewModel 根据 intent 决定要加载、播放、暂停、跳转还是切清晰度。
 *
 * 你读代码时可以先记住：
 * VideoScreen 负责发 Intent，
 * VideoViewModel.dispatch() 负责接住 Intent 并执行真正逻辑。
 */
sealed interface VideoIntent {
    data class SelectFeed(val feed: FeedKind) : VideoIntent

    /**
     * 当前登录用户发生变化。
     *
     * userId 为 null 表示游客或已经退出登录。
     */
    data class BindViewer(val userId: String?) : VideoIntent

    /**
     * 进入普通首页视频流。
     *
     * 从搜索结果返回首页、或者第一次打开首页时会用到。
     */
    data object EnterHomeFeed : VideoIntent

    /**
     * 加载第一页数据。
     */
    data object LoadFirstPage : VideoIntent

    /**
     * 加载下一页数据。
     *
     * VideoScreen 滑到列表尾部附近时触发。
     */
    data object LoadNextPage : VideoIntent

    /**
     * 播放信息流中某个位置的内容。
     *
     * position 对应 VideoState.items 的下标，也对应 VerticalPager 的页码。
     */
    data class PlayPosition(val position: Int) : VideoIntent

    data class ReportImpression(val position: Int) : VideoIntent

    /**
     * 暂停播放器。
     */
    data object PausePlayer : VideoIntent

    /**
     * 恢复播放。
     */
    data object ResumePlayer : VideoIntent

    /**
     * 应用退到后台时暂停播放。
     *
     * 与 [PausePlayer] 不同：会先记录暂停前是否在播，供 [ResumeFromBackground] 决定
     * 回到前台时是否自动恢复，避免把用户主动暂停的视频强行续播。
     */
    data object PauseForBackground : VideoIntent

    /**
     * 应用回到前台时，恢复退到后台前正在播放的内容。
     */
    data object ResumeFromBackground : VideoIntent

    /**
     * 切换播放/暂停状态。
     */
    data object TogglePlayPause : VideoIntent

    /**
     * 跳到某个播放进度。
     */
    data class SeekTo(val positionMs: Long) : VideoIntent

    /**
     * 修改播放倍速。
     */
    data class SetPlaybackSpeed(val speed: Float) : VideoIntent

    /**
     * 清掉错误提示。
     */
    data object DismissError : VideoIntent

    /**
     * 从搜索结果跳到指定内容。
     */
    data class JumpToVideo(val videoId: String) : VideoIntent

    // 清晰度相关动作：第一遍读主流程时可以先跳过细节。

    /**
     * 用户选择“自动”，开启自动清晰度策略。
     */
    data object EnableAutoQuality : VideoIntent

    /**
     * 用户手动选择某个清晰度。
     */
    data class SelectManualQuality(val name: String, val url: String) : VideoIntent

    /**
     * 播放器报告发生卡顿，用于判断是否要自动降清晰度。
     */
    data class ReportBuffering(val durationMs: Long) : VideoIntent

    /**
     * 清掉 toast 提示。
     */
    data object DismissToast : VideoIntent

    data class ToggleLike(val videoId: String) : VideoIntent
    data class ToggleFavorite(val videoId: String) : VideoIntent
    data class OpenComments(val videoId: String) : VideoIntent
    data object CloseComments : VideoIntent
    data class UpdateCommentDraft(val text: String) : VideoIntent
    data object SubmitComment : VideoIntent
}
