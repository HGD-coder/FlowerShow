package com.example.flower_show.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.flower_show.ai.SearchSuggestionEngine
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.ui.theme.ArcticColors
import kotlinx.coroutines.delay

@Composable
fun VideoCard(
    video: VideoItem,
    playerManager: VideoPlayerManager,
    playerContent: (@Composable BoxScope.() -> Unit)? = null,
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
    var isLandscapeVideo by remember(video.id) { mutableStateOf(playerManager.isCurrentVideoLandscape) }
    val subtitle = video.recommendWords.firstOrNull() ?: "This is a TikTok subtitle."
    val relatedSearch = remember(video) { SearchSuggestionEngine.relatedSearch(video) }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000L)
            controlsVisible = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        playerContent?.invoke(this)

        // Cover image
        if (playerContent == null || durationMs == 0L) {
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
                        listOf(ArcticColors.Background.copy(alpha = 0.48f), Color.Transparent),
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
                        listOf(Color.Transparent, ArcticColors.Background.copy(alpha = 0.84f)),
                    ),
                ),
        )

        if (playerContent != null && isLandscapeVideo) {
            FullscreenWatchButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 122.dp),
            )
        }

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
                .padding(start = 24.dp, end = 98.dp, bottom = 156.dp),
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
                .padding(end = 10.dp, bottom = 146.dp),
        )

        RelatedSearchBar(
            keyword = relatedSearch,
            onClick = { onRecommendWordClick(relatedSearch) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = 92.dp),
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
                .padding(bottom = 72.dp)
                .fillMaxWidth()
                .height(14.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = ArcticColors.PrimaryContainer.copy(alpha = 0.90f),
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
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
                is PlayerCallback.PlaybackEvent.VideoSizeChanged -> {
                    isLandscapeVideo = event.width > event.height && event.height > 0
                }
                is PlayerCallback.PlaybackEvent.Error -> {}
                is PlayerCallback.PlaybackEvent.BufferingStart -> {}
                is PlayerCallback.PlaybackEvent.BufferingEnd -> {}
            }
        }
        playerManager.addCallback(cb)
        onDispose { playerManager.removeCallback(cb) }
    }
}

@Composable
private fun FullscreenWatchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(ArcticColors.Glass.copy(alpha = 0.50f))
            .border(1.dp, ArcticColors.PrimaryContainer.copy(alpha = 0.42f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FullscreenGlyphIcon(tint = ArcticColors.Primary, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "全屏观看",
            color = ArcticColors.Primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RelatedSearchBar(
    keyword: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ArcticColors.Glass.copy(alpha = 0.54f))
            .border(1.dp, ArcticColors.Outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(tint = ArcticColors.Primary, size = 23.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "相关搜索 · $keyword",
            color = ArcticColors.OnSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            color = ArcticColors.OnSurfaceVariant.copy(alpha = 0.68f),
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun FullscreenGlyphIcon(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val strokeWidth = s * 0.08f
        drawLine(tint, Offset(s * 0.18f, s * 0.36f), Offset(s * 0.18f, s * 0.18f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.18f, s * 0.18f), Offset(s * 0.36f, s * 0.18f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.64f, s * 0.18f), Offset(s * 0.82f, s * 0.18f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.82f, s * 0.18f), Offset(s * 0.82f, s * 0.36f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.82f, s * 0.64f), Offset(s * 0.82f, s * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.82f, s * 0.82f), Offset(s * 0.64f, s * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.36f, s * 0.82f), Offset(s * 0.18f, s * 0.82f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.18f, s * 0.82f), Offset(s * 0.18f, s * 0.64f), strokeWidth, StrokeCap.Round)
        drawCircle(
            color = tint,
            radius = s * 0.18f,
            center = Offset(s * 0.50f, s * 0.50f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
