package com.example.flower_show.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flower_show.ai.SearchSuggestionEngine
import com.example.flower_show.model.VideoItem
import com.example.flower_show.model.VideoQuality
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.ui.theme.ArcticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VideoCard(
    video: VideoItem,
    playerManager: VideoPlayerManager,
    playerContent: (@Composable BoxScope.() -> Unit)? = null,
    isActive: Boolean = false,
    onSeek: (Long) -> Unit = {},
    onRecommendWordClick: (String) -> Unit = {},
    isLiked: Boolean = false,
    isCollected: Boolean = false,
    onLikeClick: () -> Unit = {},
    onCollectClick: () -> Unit = {},
    onSetQuality: (String, String) -> Unit = { _, _ -> },
    onEnableAutoQuality: () -> Unit = {},
    qualityMode: String = "Auto",       // "Auto" or "Manual"
    currentQualityName: String? = null,
    availableQualities: List<VideoQuality> = emptyList(),
    playbackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var hasVideoFrame by remember(video.id) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLandscapeVideo by remember(video.id) { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    var showLikeHeart by remember { mutableStateOf(false) }
    var likeHeartOffset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val currentPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val currentOnPlaybackSpeedChange by rememberUpdatedState(onPlaybackSpeedChange)
    val currentIsLiked by rememberUpdatedState(isLiked)
    val currentOnLikeClick by rememberUpdatedState(onLikeClick)
    val relatedSearch = remember(video.id, video.title, video.tags, video.recommendWords) {
        SearchSuggestionEngine.relatedSearch(video)
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_card_${video.id}"),
    ) {
        playerContent?.invoke(this)

        // Tap toggles controls; double-tap likes; long press boosts playback to 2x.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.id) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (!currentIsLiked) {
                                currentOnLikeClick()
                            }
                            likeHeartOffset = offset
                            showLikeHeart = true
                            coroutineScope.launch {
                                delay(800)
                                showLikeHeart = false
                            }
                        },
                        onTap = { controlsVisible = !controlsVisible },
                        onLongPress = {
                            isLongPressing = true
                            currentOnPlaybackSpeedChange(2f)
                        },
                        onPress = {
                            val restoreSpeed = currentPlaybackSpeed
                            tryAwaitRelease()
                            if (isLongPressing) {
                                isLongPressing = false
                                currentOnPlaybackSpeedChange(restoreSpeed)
                            }
                        },
                    )
                },
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

        if (isActive && hasVideoFrame && isLandscapeVideo) {
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

        AnimatedVisibility(
            visible = isLongPressing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-86).dp),
        ) {
            Text(
                text = "2.0x",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        // Double-tap like heart animation
        AnimatedVisibility(
            visible = showLikeHeart,
            enter = scaleIn(initialScale = 0.5f, animationSpec = tween(300)),
            exit = scaleOut(targetScale = 1.5f, animationSpec = tween(300)),
            modifier = Modifier
                .offset {
                    val halfHeartPx = (50.dp.toPx()).roundToInt()
                    IntOffset(
                        likeHeartOffset.x.roundToInt() - halfHeartPx,
                        likeHeartOffset.y.roundToInt() - halfHeartPx,
                    )
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Like",
                tint = Color(0xFFFF2D55),
                modifier = Modifier.size(100.dp),
            )
        }

        VideoCaptionPanel(
            author = video.author,
            title = video.title,
            subtitle = null,
            onSubtitleClick = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 98.dp, bottom = 156.dp),
        )

        VideoActionRail(
            avatarUrl = video.avatarUrl,
            isLiked = isLiked,
            isCollected = isCollected,
            likes = video.likes,
            comments = video.comments,
            collections = video.collections,
            shares = video.shares,
            onLikeClick = onLikeClick,
            onCollectClick = onCollectClick,
            availableQualities = availableQualities,
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
                .testTag("related_search_bar")
                .padding(start = 18.dp, end = 18.dp, bottom = 92.dp),
        )

        VideoProgressSlider(
            playerManager = playerManager,
            isActive = isActive,
            onSeek = onSeek,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth()
                .height(4.dp),
        )
    }

    LaunchedEffect(isActive, video.id) {
        isLandscapeVideo = false
        hasVideoFrame = false
        if (!isActive) {
            isPlaying = false
        }
    }

    // Low-frequency playback callbacks. Progress is isolated in VideoProgressSlider.
    DisposableEffect(playerManager, isActive, video.id) {
        if (!isActive) {
            onDispose { }
        } else {
            val cb = PlayerCallback { event ->
                when (event) {
                    is PlayerCallback.PlaybackEvent.Ready -> {
                        hasVideoFrame = true
                        isPlaying = true
                    }
                    is PlayerCallback.PlaybackEvent.StateChanged -> isPlaying = event.isPlaying
                    is PlayerCallback.PlaybackEvent.Complete -> {
                        isPlaying = false
                    }
                    is PlayerCallback.PlaybackEvent.VideoSizeChanged -> {
                        isLandscapeVideo = event.width > event.height && event.height > 0
                    }
                    is PlayerCallback.PlaybackEvent.Progress -> {}
                    is PlayerCallback.PlaybackEvent.Error -> {}
                    is PlayerCallback.PlaybackEvent.BufferingStart -> {}
                    is PlayerCallback.PlaybackEvent.BufferingEnd -> {}
                }
            }
            playerManager.addCallback(cb)
            onDispose { playerManager.removeCallback(cb) }
        }
    }
}

@Composable
private fun VideoProgressSlider(
    playerManager: VideoPlayerManager,
    isActive: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableLongStateOf(playerManager.duration.validVideoDuration()) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(0f) }
    val sliderValue by remember {
        derivedStateOf { if (isDragging) sliderPos else progress }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            progress = 0f
            sliderPos = 0f
            durationMs = 0L
            isDragging = false
        }
    }

    DisposableEffect(playerManager, isActive) {
        if (!isActive) {
            onDispose { }
        } else {
            val cb = PlayerCallback { event ->
                when (event) {
                    is PlayerCallback.PlaybackEvent.Ready -> {
                        durationMs = event.durationMs.validVideoDuration()
                    }
                    is PlayerCallback.PlaybackEvent.Progress -> {
                        if (!isDragging && durationMs > 0L) {
                            progress = (event.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        }
                    }
                    is PlayerCallback.PlaybackEvent.Complete -> {
                        progress = 1f
                        sliderPos = 1f
                        isDragging = false
                    }
                    else -> Unit
                }
            }
            playerManager.addCallback(cb)
            onDispose { playerManager.removeCallback(cb) }
        }
    }

    Slider(
        value = sliderValue.coerceIn(0f, 1f),
        enabled = isActive,
        onValueChange = {
            sliderPos = it
            isDragging = true
        },
        onValueChangeFinished = {
            isDragging = false
            if (durationMs > 0L) {
                onSeek((sliderPos.coerceIn(0f, 1f) * durationMs).toLong())
            }
        },
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = ArcticColors.PrimaryContainer.copy(alpha = 0.90f),
            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            disabledThumbColor = Color.Transparent,
            disabledActiveTrackColor = ArcticColors.PrimaryContainer.copy(alpha = 0.40f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.10f),
        ),
    )
}

private fun Long.validVideoDuration(): Long {
    return if (this > 0L && this < Long.MAX_VALUE / 2) this else 0L
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
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ArcticColors.Glass.copy(alpha = 0.54f))
            .border(1.dp, ArcticColors.Outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(tint = ArcticColors.Primary, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "相关搜索 · $keyword",
            color = ArcticColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            color = ArcticColors.OnSurfaceVariant.copy(alpha = 0.68f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun FullscreenGlyphIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Fullscreen,
        contentDescription = "全屏",
        tint = tint,
        modifier = Modifier.size(size),
    )
}
