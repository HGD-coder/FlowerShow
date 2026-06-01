package com.example.flower_show.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import kotlinx.coroutines.delay

@Composable
fun VideoCard(
    video: VideoItem,
    playerManager: VideoPlayerManager,
    onSeek: (Long) -> Unit = {},
    onRecommendWordClick: (String) -> Unit = {},
    onSetQuality: (String, String) -> Unit = { _, _ -> },
    onEnableAutoQuality: () -> Unit = {},
    qualityMode: String = "Auto",       // "Auto" or "Manual"
    currentQualityName: String? = null,
    availableQualities: List<String> = emptyList(), // quality names
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var isCollected by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(0f) }
    val subtitle = video.recommendWords.firstOrNull() ?: "This is a TikTok subtitle."

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000L)
            controlsVisible = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Cover image
        if (durationMs == 0L) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(video.coverUrl).crossfade(true).build(),
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Tap area
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { controlsVisible = !controlsVisible }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f)),
                    ),
                ),
        )

        // Fullscreen toggle / 全屏切换按钮
        Text(
            text = "⛶",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 22.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .clickable { onToggleFullscreen() },
        )

        // Center play/pause
        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (isPlaying) {
                PauseIcon(tint = Color.White.copy(alpha = 0.9f), size = 56.dp,
                    onClick = { playerManager.togglePlayPause() })
            } else {
                PlayIcon(tint = Color.White.copy(alpha = 0.9f), size = 56.dp,
                    onClick = { playerManager.togglePlayPause() })
            }
        }

        TikTokCaptionPanel(
            author = video.author,
            title = video.title,
            subtitle = subtitle,
            onSubtitleClick = onRecommendWordClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 98.dp, bottom = 112.dp),
        )

        TikTokActionRail(
            avatarUrl = video.avatarUrl,
            isLiked = isLiked,
            isCollected = isCollected,
            likes = video.likes,
            comments = video.comments,
            collections = video.collections,
            shares = video.shares,
            onLikeClick = { isLiked = !isLiked },
            onCollectClick = { isCollected = !isCollected },
            qualityUrls = video.qualityUrls,
            qualityMode = qualityMode,
            currentQualityName = currentQualityName,
            onSetQuality = onSetQuality,
            onEnableAutoQuality = onEnableAutoQuality,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 112.dp),
        )

        Slider(
            value = if (isDragging) sliderPos else progress,
            onValueChange = { sliderPos = it; isDragging = true },
            onValueChangeFinished = {
                isDragging = false
                onSeek((sliderPos * durationMs).toLong())
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 94.dp)
                .fillMaxWidth()
                .height(14.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.White.copy(alpha = 0.75f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }

    // Playback callbacks
    DisposableEffect(playerManager) {
        val cb = PlayerCallback { event ->
            when (event) {
                is PlayerCallback.PlaybackEvent.Ready -> { durationMs = event.durationMs; isPlaying = true }
                is PlayerCallback.PlaybackEvent.Progress -> {
                    if (!isDragging && durationMs > 0) progress = event.positionMs.toFloat() / durationMs
                }
                is PlayerCallback.PlaybackEvent.StateChanged -> isPlaying = event.isPlaying
                is PlayerCallback.PlaybackEvent.Complete -> { isPlaying = false; progress = 1f }
                is PlayerCallback.PlaybackEvent.Error -> {}
                is PlayerCallback.PlaybackEvent.BufferingStart -> {}
                is PlayerCallback.PlaybackEvent.BufferingEnd -> {}
            }
        }
        playerManager.addCallback(cb)
        onDispose { playerManager.removeCallback(cb) }
    }
}
