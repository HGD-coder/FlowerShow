package com.example.flower_show.ui.screen

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
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
import com.example.flower_show.viewmodel.VideoIntent
import com.example.flower_show.viewmodel.VideoViewModel

@Composable
fun VideoScreen(
    targetVideoId: String? = null,
    onSearchClick: () -> Unit,
    onRecommendWordClick: (String) -> Unit,
    viewModel: VideoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("UNUSED_EXPRESSION")
    state.isPlayerReady
    val pagerState = rememberPagerState(pageCount = { state.items.size.coerceAtLeast(1) })

    // P1-3: Landscape detection / 横竖屏检测
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // Play when page changes
    LaunchedEffect(pagerState.currentPage, state.items.size) {
        viewModel.dispatch(VideoIntent.PlayPosition(pagerState.currentPage))
    }

    // Load more when near end
    LaunchedEffect(pagerState.currentPage, state.items.size) {
        if (pagerState.currentPage >= state.items.size - 2 && !state.isLoading)
            viewModel.dispatch(VideoIntent.LoadNextPage)
    }

    // P0-3: Jump to target video from search result
    LaunchedEffect(targetVideoId) {
        val targetId = targetVideoId ?: return@LaunchedEffect
        viewModel.dispatch(VideoIntent.JumpToVideo(targetId))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (state.items.isEmpty()) {
            Text("加载中...", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center))
        } else {
            // PlayerView
            if (state.isPlayerReady) {
                AndroidView(
                    factory = { ctx ->
                        val inflater = LayoutInflater.from(ctx)
                        val view = inflater.inflate(R.layout.player_view, null) as PlayerView
                        view.player = viewModel.playerManager.getPlayer()
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT)
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isLandscape) {
                // ── Landscape: full-screen player, tap to toggle system UI + controls ──
                var showLandscapeControls by remember { mutableStateOf(true) }
                LaunchedEffect(showLandscapeControls) {
                    if (showLandscapeControls) {
                        kotlinx.coroutines.delay(3000L)
                        showLandscapeControls = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            showLandscapeControls = !showLandscapeControls
                        // Toggle system bars together with controls
                        window?.let { w ->
                            val ctrl = WindowInsetsControllerCompat(w, view)
                            if (showLandscapeControls) {
                                ctrl.show(WindowInsetsCompat.Type.systemBars())
                            } else {
                                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                                ctrl.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }
                        }
                ) {
                    // Center play/pause overlay
                    if (showLandscapeControls) {
                        if (viewModel.playerManager.isPlaying) {
                            PauseIcon(
                                tint = Color.White.copy(alpha = 0.8f),
                                size = 48.dp,
                                onClick = { viewModel.playerManager.togglePlayPause() },
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            PlayIcon(
                                tint = Color.White.copy(alpha = 0.8f),
                                size = 48.dp,
                                onClick = { viewModel.playerManager.togglePlayPause() },
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            } else {
                // ── Portrait: full feed with cards / 竖屏：完整视频流 ──
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val item = state.items[page]
                    key(item.hashCode()) {
                        when (item) {
                            is VideoItem -> VideoCard(
                                video = item,
                                playerManager = viewModel.playerManager,
                                onSeek = { ms -> viewModel.dispatch(VideoIntent.SeekTo(ms)) },
                                onRecommendWordClick = onRecommendWordClick,
                                onSetQuality = { name, url -> viewModel.dispatch(VideoIntent.SelectManualQuality(name, url)) },
                                onEnableAutoQuality = { viewModel.dispatch(VideoIntent.EnableAutoQuality) },
                                qualityMode = state.qualityMode.name,
                                currentQualityName = state.currentQualityName,
                            )
                            is ImageCardItem -> ImageCard(card = item)
                            is AlbumCardItem -> AlbumCard(card = item)
                            else -> {}
                        }
                    }
                }
            }
        }

        // Search bar — always visible, highest z-order
        SearchBar(onClick = onSearchClick, modifier = Modifier.align(Alignment.TopCenter).zIndex(1f))

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
