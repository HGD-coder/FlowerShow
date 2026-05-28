package com.example.flower_show.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var showQualityMenu by remember { mutableStateOf(false) }

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
                contentScale = ContentScale.Fit,
            )
        }

        // Tap area
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { controlsVisible = !controlsVisible }
        )

        // Bottom gradient
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
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

        // Bottom: text row + progress slider
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left: recommend words + author + caption + music
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    // Recommend words above author text — no overlap, natural flow
                    if (video.recommendWords.isNotEmpty()) {
                        RecommendWordsBar(
                            words = video.recommendWords,
                            onWordClick = onRecommendWordClick,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("@${video.author}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(video.title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("🎵", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("@${video.author} · 原声", color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }

                // Right: avatar + interaction icons
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(video.avatarUrl).crossfade(true).build(),
                        contentDescription = "头像",
                        modifier = Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(22.dp))
                    if (isLiked) HeartFilledIcon(size = 32.dp, onClick = { isLiked = !isLiked })
                    else HeartOutlineIcon(size = 32.dp, onClick = { isLiked = !isLiked })
                    Text(formatCount(if (isLiked) video.likes + 1 else video.likes),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    CommentIcon(size = 32.dp)
                    Text(formatCount(video.comments), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    BookmarkIcon(tint = if (isCollected) Color(0xFFFFD700) else Color.White, size = 32.dp,
                        onClick = { isCollected = !isCollected })
                    Text(formatCount(if (isCollected) video.collections + 1 else video.collections),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    ShareIcon(size = 32.dp)
                    Text("分享", color = Color.White, fontSize = 12.sp)
                    // Quality selector: Auto + Manual
                    if (video.qualityUrls != null && video.qualityUrls!!.size > 1) {
                        Spacer(Modifier.height(22.dp))
                        Text("画质", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                            modifier = Modifier.clickable { showQualityMenu = true })
                        Box {
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false },
                            ) {
                                // Auto option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (qualityMode == "Auto") "✓ 自动（当前 ${currentQualityName ?: "自动"}）"
                                            else "  自动",
                                            color = Color.Black,
                                        )
                                    },
                                    onClick = {
                                        showQualityMenu = false
                                        onEnableAutoQuality()
                                    },
                                )
                                // Manual quality options
                                video.qualityUrls!!.forEach { (name, url) ->
                                    DropdownMenuItem(
                                        text = {
                                            val label = if (qualityMode == "Manual" && currentQualityName == name)
                                                "✓ $name" else "  $name"
                                            Text(label, color = Color.Black)
                                        },
                                        onClick = {
                                            showQualityMenu = false
                                            onSetQuality(name, url)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress slider
            Slider(
                value = if (isDragging) sliderPos else progress,
                onValueChange = { sliderPos = it; isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek((sliderPos * durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.9f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
        }
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
