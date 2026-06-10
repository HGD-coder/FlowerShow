package com.example.flower_show.ui.screen

import android.view.LayoutInflater
import android.view.ViewGroup
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.flower_show.R
import com.example.flower_show.model.*
import com.example.flower_show.ui.component.*
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.viewmodel.VideoIntent
import com.example.flower_show.viewmodel.VideoViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoScreen(
    targetVideoId: String? = null,
    onSearchClick: () -> Unit,
    onRecommendWordClick: (String) -> Unit,
    viewModel: VideoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportDrawnWhen { state.items.isNotEmpty() && !state.isLoading }

    @Suppress("UNUSED_EXPRESSION")
    state.isPlayerReady
    val pagerState = rememberPagerState(pageCount = { state.items.size.coerceAtLeast(1) })
    var pendingTargetVideoId by remember { mutableStateOf<String?>(targetVideoId) }

    // P1-3: Landscape detection / 横竖屏检测
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // System UI immersive control / 沉浸式系统 UI 控制
    val view = LocalView.current
    val window = remember { (view.context as? android.app.Activity)?.window }
    DisposableEffect(isLandscape) {
        if (isLandscape) {
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
        onDispose { }
    }

    // P0-3: Jump to target video from search result
    LaunchedEffect(targetVideoId) {
        pendingTargetVideoId = targetVideoId
        val targetId = targetVideoId
        if (targetId == null) {
            viewModel.dispatch(VideoIntent.EnterHomeFeed)
            return@LaunchedEffect
        }
        viewModel.dispatch(VideoIntent.JumpToVideo(targetId))
    }

    LaunchedEffect(state.currentPosition, state.items.size, isLandscape) {
        if (isLandscape || state.items.isEmpty()) return@LaunchedEffect
        val targetPage = state.currentPosition.coerceIn(0, state.items.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
        val pendingId = pendingTargetVideoId
        val currentVideoId = (state.items.getOrNull(targetPage) as? VideoItem)?.id
        if (pendingId != null && pendingId == currentVideoId) {
            pendingTargetVideoId = null
        }
    }

    // Play when page changes. During a search jump, ignore stale page 0 until the pager reaches the target.
    LaunchedEffect(pagerState.settledPage, state.items.size, pendingTargetVideoId) {
        if (state.items.isEmpty()) return@LaunchedEffect
        val pendingId = pendingTargetVideoId
        if (pendingId != null) {
            val settledVideoId = (state.items.getOrNull(pagerState.settledPage) as? VideoItem)?.id
            if (settledVideoId != pendingId) return@LaunchedEffect
        }
        viewModel.dispatch(VideoIntent.PlayPosition(pagerState.settledPage))
    }

    // Load more when near end
    LaunchedEffect(pagerState.currentPage, state.items.size) {
        if (pagerState.settledPage >= state.items.size - 2 && !state.isLoading)
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
            if (isLandscape && state.isPlayerReady) {
                PlayerSurface(
                    playerViewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("player_surface"),
                )
            }

            if (isLandscape) {
                // ── Landscape: full-screen player, tap to toggle system UI + controls ──
                var showLandscapeControls by remember { mutableStateOf(true) }
                val currentVideo = state.items.getOrNull(state.currentPosition) as? VideoItem
                    ?: state.items.getOrNull(pagerState.settledPage) as? VideoItem
                val ctx = LocalContext.current
                LandscapeVideoControls(
                    video = currentVideo,
                    playerManager = viewModel.playerManager,
                    visible = showLandscapeControls,
                    onToggleVisible = { showLandscapeControls = !showLandscapeControls },
                    onBack = {
                        val activity = ctx as? android.app.Activity ?: return@LandscapeVideoControls
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    },
                    onSeek = { ms -> viewModel.dispatch(VideoIntent.SeekTo(ms)) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // ── Portrait: full feed with cards / 竖屏：完整视频流 ──
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("feed_pager"),
                    beyondViewportPageCount = 1,
                    key = { page ->
                        state.items.getOrNull(page)?.feedStableKey(page) ?: "placeholder:$page"
                    },
                ) { page ->
                    val item = state.items[page]
                    when (item) {
                        is VideoItem -> {
                            val ctx = LocalContext.current
                            val isActivePage = page == pagerState.settledPage
                            val isCurrentPlaybackPage = page == state.currentPosition
                            VideoCard(
                                video = item,
                                playerManager = viewModel.playerManager,
                                playerContent = if (state.isPlayerReady && isActivePage) {
                                    {
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
                                onSetQuality = { name, url -> viewModel.dispatch(VideoIntent.SelectManualQuality(name, url)) },
                                onEnableAutoQuality = { viewModel.dispatch(VideoIntent.EnableAutoQuality) },
                                qualityMode = if (isCurrentPlaybackPage) state.qualityMode.name else "Auto",
                                currentQualityName = if (isCurrentPlaybackPage) state.currentQualityName else null,
                                availableQualities = if (isCurrentPlaybackPage) {
                                    state.availableQualities
                                } else {
                                    emptyList()
                                },
                                onToggleFullscreen = {
                                    val activity = ctx as? android.app.Activity ?: return@VideoCard
                                    if (isLandscape) {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                            )
                        }
                        is ImageCardItem -> ImageCard(card = item)
                        is AlbumCardItem -> AlbumCard(card = item)
                        else -> {}
                    }
                }
            }
        }

        if (!isLandscape) {
            TikTokTopNavigation(
                onSearchClick = onSearchClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            )
            TikTokBottomNavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
            )
        }

        // Auto-quality toast / 自动画质切换提示
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

        // Error snackbar
        state.error?.let { error ->
            Text(error, color = Color.Red, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center))
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.dispatch(VideoIntent.PausePlayer) }
    }
}

private fun CardItem.feedStableKey(index: Int): String = when (this) {
    is VideoItem -> "video:$id"
    is ImageCardItem -> "image:$id"
    is AlbumCardItem -> "album:$id"
    CardItem.TypeVideo -> "type_video:$index"
    CardItem.TypeImage -> "type_image:$index"
    CardItem.TypeAlbum -> "type_album:$index"
}

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
        modifier = modifier,
    )
}
