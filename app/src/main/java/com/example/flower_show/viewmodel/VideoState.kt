package com.example.flower_show.viewmodel

import androidx.compose.runtime.Immutable
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.ContentComment
import com.example.flower_show.model.DeliveryContext
import com.example.flower_show.model.QualityMode
import com.example.flower_show.model.VideoQuality

enum class FeedKind {
    Following,
    Recommended,
}

/**
 * 视频首页的完整 UI 状态。
 *
 * ViewModel 通过 MutableStateFlow<VideoState> 持有它，
 * VideoScreen 通过 collectAsStateWithLifecycle() 订阅它。
 *
 * 你可以把它理解成“页面当前应该长什么样”的快照：
 * - 列表里有哪些卡片
 * - 当前播放到第几条
 * - 是否正在加载
 * - 播放器是否准备好
 * - 清晰度菜单里有哪些选项
 *
 * data class + copy() 是 Compose/MVI 常见写法：
 * ViewModel 不直接修改某个字段，而是 copy 出一个新 state，
 * UI 收到新 state 后自动重组。
 */
@Immutable
data class VideoState(
    /**
     * 首页信息流数据。
     *
     * 这里是 List<CardItem>，说明首页可以混排：
     * - VideoItem 视频卡片
     * - ImageCardItem 图片卡片
     * - AlbumCardItem 图集卡片
     */
    val items: List<CardItem> = emptyList(),
    val deliveryContexts: List<DeliveryContext?> = emptyList(),
    val recommendedServeSessionId: String? = null,
    val recommendedNextCursor: String? = null,

    /**
     * 是否正在从 Repository 加载数据。
     */
    val isLoading: Boolean = false,

    /**
     * 是否还有下一页数据。
     *
     * VerticalPager 滑到接近末尾时，如果 hasMore 为 true，就会触发 LoadNextPage。
     */
    val hasMore: Boolean = true,

    /**
     * 当前正在播放/选中的信息流位置。
     *
     * 它和 VideoScreen 里的 pagerState.currentPage 会互相同步。
     */
    val currentPosition: Int = 0,

    /**
     * 播放器是否初始化完成。
     */
    val isPlayerReady: Boolean = false,

    /**
     * 当前播放倍速。
     */
    val playbackSpeed: Float = 1f,

    /**
     * 错误信息。为 null 表示没有错误。
     */
    val error: String? = null,

    /**
     * 临时提示文案，例如自动切换清晰度后的 toast。
     */
    val toastMessage: String? = null,

    /**
     * 从搜索结果跳转过来时，想定位到的视频/卡片 id。
     *
     * 定位成功后 ViewModel 会把它清空。
     */
    val targetVideoId: String? = null,

    // 下面是清晰度相关状态，第一遍读视频主流程时可以先知道它们服务于 VideoCard 的清晰度菜单。

    /**
     * 当前清晰度模式：自动或手动。
     */
    val qualityMode: QualityMode = QualityMode.Auto,

    /**
     * 当前实际播放的清晰度名称，例如 "720p"。
     */
    val currentQualityName: String? = null,

    /**
     * 当前视频可选的清晰度列表。
     */
    val availableQualities: List<VideoQuality> = emptyList(),

    val selectedFeed: FeedKind = FeedKind.Recommended,
    val interactionRequests: Set<String> = emptySet(),
    val commentSheetVideoId: String? = null,
    val commentsByVideoId: Map<String, List<ContentComment>> = emptyMap(),
    val commentDraft: String = "",
    val isCommentsLoading: Boolean = false,
    val isCommentSubmitting: Boolean = false,
)
