package com.example.flower_show.ui.screen

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.flower_show.R
import com.example.flower_show.model.*
import com.example.flower_show.ui.component.*
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.util.PerformanceDiagnostics
import com.example.flower_show.viewmodel.CommentState
import com.example.flower_show.viewmodel.FeedKind
import com.example.flower_show.viewmodel.VideoIntent
import com.example.flower_show.viewmodel.VideoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min

/**
 * 视频首页 UI。
 *
 * 它是视频主流程的 Compose 页面层，核心职责是：
 * - 订阅 VideoViewModel.state，把状态渲染成界面。
 * - 用 VerticalPager 做上下滑信息流。
 * - 当 pager 当前页变化时，发送 PlayPosition 给 ViewModel。
 * - 接收 targetVideoId，从搜索结果跳回指定内容。
 * - 把传统 Android 的 PlayerView 通过 AndroidView 嵌入 Compose。
 *
 * 读这个文件时不要先陷入 UI 细节，先抓住这条线：
 * state.items -> VerticalPager -> 当前 page -> VideoIntent.PlayPosition -> ViewModel 播放。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoScreen(
    targetVideoId: String? = null,
    onSearchClick: () -> Unit,
    onRecommendWordClick: (String) -> Unit,
    onFriendsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCreatorClick: (VideoItem) -> Unit = {},
    onShareClick: (VideoItem) -> Unit = {},
    isAuthenticated: Boolean = false,
    onAuthenticationRequired: () -> Unit = {},
    viewModel: VideoViewModel = viewModel(),
) {
    // 从 ViewModel 的 StateFlow 收集状态。
    // collectAsStateWithLifecycle 会跟随生命周期自动开始/停止收集，避免页面不可见时还持续更新 UI。
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 性能标记：当首页数据加载完成后，告诉系统“首屏可以认为绘制完成了”。
    ReportDrawnWhen { state.items.isNotEmpty() && !state.isLoading }

    // VerticalPager 的状态。
    // pageCount 依赖 state.items.size；至少给 1 页，避免空列表时 Pager 页数为 0 引发边界问题。
    val pagerState = rememberPagerState(pageCount = { state.items.size.coerceAtLeast(1) })

    // 从搜索结果跳过来时，targetVideoId 用来标记“还没定位完成的目标”。
    // 定位完成后会清空，避免后续滑动被这个旧目标影响。
    var pendingTargetVideoId by remember { mutableStateOf<String?>(targetVideoId) }

    // 首页首屏性能埋点只上报一次。
    var feedReadyReported by remember { mutableStateOf(false) }

    // 横竖屏检测：宽大于高时认为是横屏。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // 横屏时以 ViewModel 的 currentPosition 为准；竖屏时以 pager 当前页为准。
    // 因为横屏全屏播放时不展示 VerticalPager。
    val activeFeedIndex = if (isLandscape) state.currentPosition else pagerState.currentPage
    val activeFeedItem = state.items.getOrNull(activeFeedIndex)

    // 只有视频卡片才允许横屏沉浸播放。图片/图集没有全屏播放器。
    val isLandscapePlayback = isLandscape && activeFeedItem is VideoItem

    // 当前 Compose 所在的 Android View，用来拿 Activity/window 控制系统栏。
    val view = LocalView.current

    // 横屏播放时，系统返回键不是退出页面，而是回到竖屏。
    BackHandler(enabled = isLandscapePlayback) {
        val activity = view.context as? android.app.Activity ?: return@BackHandler
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // 如果用户横屏时当前内容不是视频，强制回竖屏。
    // LaunchedEffect 会在 key 变化后运行，适合处理这种“状态变化引发的命令式动作”。
    LaunchedEffect(isLandscape, activeFeedIndex, activeFeedItem?.itemType) {
        if (isLandscape && activeFeedItem != null && activeFeedItem !is VideoItem) {
            val activity = view.context as? android.app.Activity ?: return@LaunchedEffect
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val window = remember { (view.context as? android.app.Activity)?.window }

    // 横屏播放时隐藏系统状态栏/导航栏，离开横屏播放时恢复。
    // DisposableEffect 适合管理这种和外部系统资源有关的副作用。
    DisposableEffect(isLandscapePlayback) {
        if (isLandscapePlayback) {
            window?.let { w ->
                val controller = WindowInsetsControllerCompat(w, view)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window?.let { w ->
                val controller = WindowInsetsControllerCompat(w, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // 横屏播放中离开本页（如跳登录/聊天）时组合销毁，key 不会再变回 false，
            // 必须在这里兜底恢复系统栏，否则状态栏/导航栏会一直保持隐藏。
            window?.let { w ->
                WindowInsetsControllerCompat(w, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 处理从搜索结果跳转过来的目标 id。
    // targetVideoId 变化时：
    // - null：进入普通首页
    // - 非 null：通知 ViewModel 去定位并播放这条内容
    LaunchedEffect(targetVideoId) {
        pendingTargetVideoId = targetVideoId
        val targetId = targetVideoId
        if (targetId == null) {
            viewModel.dispatch(VideoIntent.EnterHomeFeed)
            return@LaunchedEffect
        }
        viewModel.dispatch(VideoIntent.JumpToVideo(targetId))
    }

    // ViewModel 已经定位到 currentPosition 后，同步 VerticalPager 的页码。
    // 典型场景：搜索结果点进来，ViewModel 找到 index，UI 需要 scrollToPage(index)。
    LaunchedEffect(state.currentPosition, state.items.size, isLandscapePlayback) {
        if (isLandscapePlayback || state.items.isEmpty()) return@LaunchedEffect
        val targetPage = state.currentPosition.coerceIn(0, state.items.lastIndex)

        // 搜索跳转时，先等 Pager 完成一次测量并接收到足够的 pageCount。
        // 否则 items 从空列表更新时，scrollToPage 可能在旧布局上返回，随后又由第 0 页触发播放。
        if (pendingTargetVideoId != null) {
            snapshotFlow {
                pagerState.layoutInfo.visiblePagesInfo.isNotEmpty() &&
                    pagerState.pageCount > targetPage
            }.first { pagerCanReachTarget -> pagerCanReachTarget }
        }
        if (pagerState.currentPage != targetPage) {
            // 用户手势滑动中不要强制同步：分页 append 恰好落在播放 debounce 窗口内时，
            // currentPosition 仍指向旧页，此时 scrollToPage 会把用户拽回上一页。
            // 手势结束后 PlayPosition 会把 currentPosition 自然对齐到实际页码。
            if (!pagerState.isScrollInProgress) {
                pagerState.scrollToPage(targetPage)
            }
        }

        val pendingId = pendingTargetVideoId
        if (pendingId != null) {
            // scrollToPage 完成不代表目标页已在 Pager 中完成布局；等待实际布局确认。
            snapshotFlow {
                pagerState.currentPage == targetPage &&
                    pagerState.layoutInfo.visiblePagesInfo.any { it.index == targetPage }
            }.first { targetPageLaidOut -> targetPageLaidOut }
        }
        val currentItem = state.items.getOrNull(targetPage)
        if (
            pendingId != null &&
            pagerState.currentPage == targetPage &&
            currentItem?.matchesTarget(pendingId) == true
        ) {
            pendingTargetVideoId = null
        }
    }

    // 首页数据第一次准备好时记录性能事件。
    LaunchedEffect(state.items.isNotEmpty(), state.isLoading) {
        if (!feedReadyReported && state.items.isNotEmpty() && !state.isLoading) {
            feedReadyReported = true
            PerformanceDiagnostics.recordSinceProcessStart(
                "startup_video_feed_ready",
                mapOf("itemCount" to state.items.size),
            )
        }
    }

    // 竖屏滑动主逻辑：
    // pagerState.currentPage 一变化，就告诉 ViewModel 播放当前页。
    //
    // 快速 fling 时 currentPage 会连续经过多个中间页，若每一页都立即
    // setMediaItem + prepare，会反复打断上一个视频的加载、浪费带宽。
    // 这里等页面稳定一小段时间再请求播放：LaunchedEffect 会在页面再次
    // 变化时自动取消本次等待，中间页被自然跳过。
    LaunchedEffect(
        pagerState.currentPage,
        state.items.size,
        pendingTargetVideoId,
        state.targetVideoId,
    ) {
        if (state.items.isEmpty()) return@LaunchedEffect
        val pendingId = pendingTargetVideoId
        if (pendingId != null) {
            val currentItem = state.items.getOrNull(pagerState.currentPage)
            if (currentItem?.matchesTarget(pendingId) != true) {
                // ViewModel 已放弃定位（例如跳转目标已下架/过期）且当前列表里确实没有
                // 这个目标：同步清掉 UI 侧的 pending 标记，否则 PlayPosition 会被永久抑制，
                // 整个信息流再也无法播放。定位成功时 items 中必有目标，不会走到这里。
                if (
                    state.targetVideoId == null &&
                    state.items.none { it.matchesTarget(pendingId) }
                ) {
                    pendingTargetVideoId = null
                }
                return@LaunchedEffect
            }
        }
        delay(SETTLE_PLAY_DEBOUNCE_MS)
        viewModel.dispatch(VideoIntent.PlayPosition(pagerState.currentPage))
    }

    val exposurePage = pagerState.currentPage
    val exposureDelivery = state.deliveryContexts.getOrNull(exposurePage)
    LaunchedEffect(
        state.selectedFeed,
        state.recommendedServeSessionId,
        exposurePage,
        exposureDelivery?.id,
    ) {
        if (state.selectedFeed != FeedKind.Recommended || exposureDelivery == null) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layoutInfo = pagerState.layoutInfo
            val pageInfo = layoutInfo.visiblePagesInfo
                .firstOrNull { it.index == exposurePage }
            if (pageInfo == null || layoutInfo.pageSize <= 0) {
                false
            } else {
                val visibleStart = max(pageInfo.offset, layoutInfo.viewportStartOffset)
                val visibleEnd = min(
                    pageInfo.offset + layoutInfo.pageSize,
                    layoutInfo.viewportEndOffset,
                )
                val visiblePixels = (visibleEnd - visibleStart).coerceAtLeast(0)
                pagerState.currentPage == exposurePage &&
                    visiblePixels * 2 >= layoutInfo.pageSize
            }
        }.distinctUntilChanged().collectLatest { sufficientlyVisible ->
            if (sufficientlyVisible) {
                delay(1_000L)
                viewModel.dispatch(VideoIntent.ReportImpression(exposurePage))
            }
        }
    }

    // 记录当前页选择事件，用于性能/行为诊断。
    LaunchedEffect(pagerState.currentPage, state.items.size) {
        val item = state.items.getOrNull(pagerState.currentPage)
        PerformanceDiagnostics.event(
            "feed_page_selected",
            mapOf(
                "page" to pagerState.currentPage,
                "itemType" to item?.javaClass?.simpleName,
                "videoId" to (item as? VideoItem)?.id,
                "itemCount" to state.items.size,
            ),
        )
    }

    // 滑到接近末尾时加载下一页。
    // 这里用 size - 2，表示还剩两条左右就提前请求，避免用户滑到底才开始等。
    LaunchedEffect(pagerState.currentPage, state.items.size) {
        if (pagerState.currentPage >= state.items.size - 2 && !state.isLoading)
            viewModel.dispatch(VideoIntent.LoadNextPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background)
            .semantics { testTagsAsResourceId = true }
            .testTag("video_screen"),
    ) {
        if (state.items.isEmpty()) {
            val emptyText = if (state.isLoading) "加载中..." else "暂无视频数据"
            Text(emptyText, color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center))
        } else {
            if (isLandscapePlayback) {
                PlayerSurface(
                    playerViewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("player_surface"),
                )
            }

            if (isLandscapePlayback) {
                // 横屏：只展示全屏播放器和横屏控制层，不展示上下滑 feed。
                var showLandscapeControls by remember { mutableStateOf(true) }
                val currentVideo = state.items.getOrNull(state.currentPosition) as? VideoItem
                    ?: state.items.getOrNull(pagerState.settledPage) as? VideoItem
                val ctx = LocalContext.current
                LandscapeVideoControls(
                    video = currentVideo,
                    playerManager = viewModel.playerManager,
                    visible = showLandscapeControls,
                    isLiked = currentVideo?.likedByViewer == true,
                    isCollected = currentVideo?.favoritedByViewer == true,
                    onLikeClick = {
                        if (isAuthenticated) {
                            currentVideo?.id?.let { viewModel.dispatch(VideoIntent.ToggleLike(it)) }
                        } else {
                            onAuthenticationRequired()
                        }
                    },
                    onCollectClick = {
                        if (isAuthenticated) {
                            currentVideo?.id?.let {
                                viewModel.dispatch(VideoIntent.ToggleFavorite(it))
                            }
                        } else {
                            onAuthenticationRequired()
                        }
                    },
                    onCommentClick = {
                        currentVideo?.id?.let {
                            viewModel.dispatch(VideoIntent.OpenComments(it))
                        }
                    },
                    onToggleVisible = { showLandscapeControls = !showLandscapeControls },
                    onBack = {
                        val activity = ctx as? android.app.Activity ?: return@LandscapeVideoControls
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    },
                    onSeek = { ms -> viewModel.dispatch(VideoIntent.SeekTo(ms)) },
                    playbackSpeed = state.playbackSpeed,
                    onPlaybackSpeedChange = { speed -> viewModel.dispatch(VideoIntent.SetPlaybackSpeed(speed)) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 竖屏：完整信息流。VerticalPager 每一页渲染一个 CardItem。
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("feed_pager"),
                    beyondViewportPageCount = 1,
                    key = { page ->
                        // 给每页稳定 key，避免列表追加/重排时 Compose 把旧页面状态错用到新内容上。
                        state.deliveryContexts.getOrNull(page)?.id?.let { "delivery:$it:$page" }
                            ?: state.items.getOrNull(page)?.feedStableKey(page)
                            ?: "placeholder:$page"
                    },
                ) { page ->
                    // feed 刷新时 items 可能在同一重组帧被更短的列表替换，
                    // 直接索引会越界崩溃，与其他位置保持一致用 getOrNull。
                    val item = state.items.getOrNull(page) ?: return@VerticalPager
                    when (item) {
                        is VideoItem -> {
                            val ctx = LocalContext.current
                            val isActivePage = page == pagerState.currentPage
                            val isCurrentPlaybackPage = page == state.currentPosition
                            VideoCard(
                                video = item,
                                playerManager = viewModel.playerManager,
                                playerContent = if (isActivePage) {
                                    {
                                        // 只有当前激活页真正挂载 PlayerSurface。
                                        // 这样全列表共用同一个播放器，不会每页创建一个 ExoPlayer。
                                        PlayerSurface(
                                            playerViewModel = viewModel,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("player_surface"),
                                        )
                                    }
                                } else {
                                    null
                                },
                                isActive = isActivePage,
                                onSeek = { ms -> viewModel.dispatch(VideoIntent.SeekTo(ms)) },
                                onRecommendWordClick = onRecommendWordClick,
                                relatedSearch =
                                    state.deliveryContexts.getOrNull(page)?.relatedSearch,
                                isLiked = item.likedByViewer,
                                isCollected = item.favoritedByViewer,
                                onLikeClick = {
                                    if (isAuthenticated) {
                                        viewModel.dispatch(VideoIntent.ToggleLike(item.id))
                                    } else {
                                        onAuthenticationRequired()
                                    }
                                },
                                onCollectClick = {
                                    if (isAuthenticated) {
                                        viewModel.dispatch(VideoIntent.ToggleFavorite(item.id))
                                    } else {
                                        onAuthenticationRequired()
                                    }
                                },
                                onCommentClick = {
                                    viewModel.dispatch(VideoIntent.OpenComments(item.id))
                                },
                                commentButtonTestTag = if (isActivePage) {
                                    "comment_video_button"
                                } else {
                                    null
                                },
                                onCreatorClick = { onCreatorClick(item) },
                                onShareClick = { onShareClick(item) },
                                onSetQuality = { name, url -> viewModel.dispatch(VideoIntent.SelectManualQuality(name, url)) },
                                onEnableAutoQuality = { viewModel.dispatch(VideoIntent.EnableAutoQuality) },
                                qualityMode = if (isCurrentPlaybackPage) state.qualityMode.name else "Auto",
                                currentQualityName = if (isCurrentPlaybackPage) state.currentQualityName else null,
                                playbackSpeed = state.playbackSpeed,
                                onPlaybackSpeedChange = { speed -> viewModel.dispatch(VideoIntent.SetPlaybackSpeed(speed)) },
                                availableQualities = if (isCurrentPlaybackPage) {
                                    // 只给当前正在播放的卡片显示清晰度列表，避免非当前页显示过期状态。
                                    state.availableQualities
                                } else {
                                    emptyList()
                                },
                                onToggleFullscreen = {
                                    val activity = ctx as? android.app.Activity ?: return@VideoCard
                                    if (isLandscapePlayback) {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                            )
                        }
                        // 图片和图集也是首页一级卡片，但它们不挂播放器表面。
                        is ImageCardItem -> ImageCard(card = item)
                        is AlbumCardItem -> AlbumCard(card = item)
                        else -> {}
                    }
                }
            }
        }

        if (!isLandscapePlayback) {
            VideoFeedTopBar(
                onSearchClick = onSearchClick,
                selectedFeed = state.selectedFeed,
                onFeedSelected = { feed ->
                    if (feed == FeedKind.Following && !isAuthenticated) {
                        onAuthenticationRequired()
                    } else {
                        viewModel.dispatch(VideoIntent.SelectFeed(feed))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            )
            VideoFeedBottomBar(
                onFriendsClick = onFriendsClick,
                onCreateClick = onCreateClick,
                onMessagesClick = onMessagesClick,
                onProfileClick = onProfileClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
            )
        }

        // 自动画质切换提示。显示 3 秒后自动清掉。
        state.toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000L)
                viewModel.dispatch(VideoIntent.DismissToast)
            }
            Text(msg, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // 错误提示。当前实现比较简单，直接显示在中间。
        state.error?.let { error ->
            Text(error, color = Color.Red, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center))
        }

        val commentVideoId = state.commentSheetVideoId
        val commentVideo = state.items.filterIsInstance<VideoItem>()
            .firstOrNull { it.id == commentVideoId }
        val viewerUserId = viewModel.viewerUserId
        val visibleComments = commentVideoId
            ?.let { state.commentsByVideoId[it] }
            .orEmpty()
        val commentState = CommentState(
            visible = commentVideoId != null,
            videoId = commentVideoId,
            totalCount = commentVideo?.comments ?: visibleComments.size,
            comments = visibleComments.map { comment ->
                VideoComment(
                    id = comment.id,
                    author = comment.nickname.ifBlank { comment.userId },
                    content = comment.body,
                    timeLabel = comment.createdAt,
                    avatarUrl = comment.avatarUrl,
                    isMine = viewerUserId != null && comment.userId == viewerUserId,
                )
            },
            draft = state.commentDraft,
            isLoading = state.isCommentsLoading,
            isSubmitting = state.isCommentSubmitting,
        )
        CommentOverlay(
            state = commentState,
            onDismiss = { viewModel.dispatch(VideoIntent.CloseComments) },
            onDraftChanged = { value ->
                viewModel.dispatch(VideoIntent.UpdateCommentDraft(value))
            },
            onSend = {
                if (isAuthenticated) {
                    viewModel.dispatch(VideoIntent.SubmitComment)
                } else {
                    onAuthenticationRequired()
                }
            },
            modifier = Modifier.zIndex(10f),
        )
    }

    // VideoScreen 离开组合时暂停播放器，避免切到搜索页后视频还在播放。
    DisposableEffect(Unit) {
        onDispose { viewModel.dispatch(VideoIntent.PausePlayer) }
    }

    // 应用退到后台时暂停播放、回到前台时恢复（仅当退到后台前正在播放）。
    // 退到后台时组合不会销毁，不挂生命周期观察器的话 ExoPlayer 会继续出声。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.dispatch(VideoIntent.PauseForBackground)
                Lifecycle.Event.ON_START -> viewModel.dispatch(VideoIntent.ResumeFromBackground)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * 给 Pager 每一页生成稳定 key。
 *
 * Compose 的 Lazy/Pager 组件很依赖稳定 key：
 * 有稳定 key 后，列表追加新数据时，Compose 更容易判断“这一页还是原来那条内容”。
 */
private fun CardItem.feedStableKey(index: Int): String = when (this) {
    // 与 VideoViewModel.feedIdentity() 相同的回退语义：id 为空时退到内容 URL，
    // 否则两条空 id 的不同内容会生成相同 key，VerticalPager 直接抛 IllegalArgumentException。
    is VideoItem -> "video:${id.ifBlank { videoUrl }}"
    is ImageCardItem -> "image:${id.ifBlank { imageUrl }}"
    is AlbumCardItem -> "album:${id.ifBlank { slides.joinToString("|") { it.mediaUrl } }}"
    CardItem.TypeVideo -> "type_video:$index"
    CardItem.TypeImage -> "type_image:$index"
    CardItem.TypeAlbum -> "type_album:$index"
}

/**
 * 滑动后等待页面稳定的时间：快速 fling 会连续经过中间页，
 * 只有页面在该时长内不再变化才请求播放，避免中间页反复 prepare。
 */
private const val SETTLE_PLAY_DEBOUNCE_MS = 150L

/**
 * 判断某张卡片是否匹配搜索结果传来的目标 id。
 */
private fun CardItem.matchesTarget(target: String): Boolean = when (this) {
    is VideoItem -> target == id || target == "video:$id"
    is ImageCardItem -> target == id || target == "image:$id"
    is AlbumCardItem -> target == id || target == "album:$id"
    else -> false
}

/**
 * Compose 和传统 Android PlayerView 的桥接层。
 *
 * ExoPlayer 的官方 PlayerView 是传统 View，不是 Compose 组件，
 * 所以这里用 AndroidView 把它嵌进 Compose。
 *
 * factory：第一次创建 View。
 * update：重组时更新 View 上绑定的 player。
 */
@Composable
private fun PlayerSurface(
    playerViewModel: VideoViewModel,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val inflater = LayoutInflater.from(ctx)
            val view = inflater.inflate(R.layout.player_view, null) as PlayerView
            view.player = playerViewModel.playerManager.getPlayer()
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view
        },
        update = { playerView ->
            playerView.player = playerViewModel.playerManager.getPlayer()
        },
        onRelease = { playerView ->
            // PlayerView.setPlayer() 会把它注册为播放器的 Listener；共享播放器的
            // 生命周期比单个 Composable 长，离开组合时必须显式解绑，
            // 否则被 dispose 的 view 仍被播放器强引用并继续接收事件（泄漏）。
            playerView.player = null
        },
        modifier = modifier,
    )
}
