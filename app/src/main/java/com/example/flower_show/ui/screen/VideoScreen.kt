package com.example.flower_show.ui.screen

import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
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
    LaunchedEffect(targetVideoId, state.items.size) {
        val targetId = targetVideoId ?: return@LaunchedEffect
        if (state.items.isEmpty()) return@LaunchedEffect
        val index = state.items.indexOfFirst { it is VideoItem && it.id == targetId }
        if (index >= 0) {
            pagerState.scrollToPage(index)
        }
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
                        PlayerView(ctx).apply {
                            player = viewModel.playerManager.getPlayer()
                            useController = false
                            controllerAutoShow = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isLandscape) {
                // ── Landscape: player-only, minimal controls / 横屏：纯播放器 ──
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
                        .clickable { showLandscapeControls = !showLandscapeControls }
                ) {
                    // Minimal play/pause
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
                            )
                            is ImageCardItem -> ImageCard(card = item)
                            is AlbumCardItem -> AlbumCard(card = item)
                            else -> {}
                        }
                    }
                }

                // Search bar
                SearchBar(onClick = onSearchClick, modifier = Modifier.align(Alignment.TopCenter))
            }
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
