package com.example.flower_show.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.PlayerCallback
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.ui.theme.ArcticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun LandscapeVideoControls(
    video: VideoItem?,
    playerManager: VideoPlayerManager,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(playerManager.isPlaying) }
    var durationMs by remember { mutableLongStateOf(playerManager.duration.validDuration()) }
    var positionMs by remember { mutableLongStateOf(playerManager.currentPosition.coerceAtLeast(0L)) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    DisposableEffect(playerManager) {
        val callback = PlayerCallback { event ->
            when (event) {
                is PlayerCallback.PlaybackEvent.Ready -> {
                    durationMs = event.durationMs.validDuration()
                    isPlaying = playerManager.isPlaying
                }

                is PlayerCallback.PlaybackEvent.Progress -> {
                    positionMs = event.positionMs.coerceAtLeast(0L)
                    if (!isDragging && durationMs > 0) {
                        sliderPosition = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    }
                }

                is PlayerCallback.PlaybackEvent.StateChanged -> isPlaying = event.isPlaying
                is PlayerCallback.PlaybackEvent.Complete -> {
                    isPlaying = false
                    sliderPosition = 1f
                }

                else -> Unit
            }
        }
        playerManager.addCallback(callback)
        onDispose { playerManager.removeCallback(callback) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onToggleVisible() },
    ) {
        if (!visible) return@Box

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(ArcticColors.Background.copy(alpha = 0.62f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ArcticColors.Background.copy(alpha = 0.72f)),
                    ),
                ),
        )

        LandscapeTopBar(
            video = video,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        LandscapeSideControls()

        LandscapeCenterToggle(
            isPlaying = isPlaying,
            onClick = {
                playerManager.togglePlayPause()
                isPlaying = playerManager.isPlaying
            },
            modifier = Modifier.align(Alignment.Center),
        )

        LandscapeBottomControls(
            video = video,
            positionMs = positionMs,
            durationMs = durationMs,
            sliderPosition = if (isDragging) sliderPosition else {
                if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            },
            onSliderChange = {
                sliderPosition = it
                isDragging = true
            },
            onSliderFinished = {
                isDragging = false
                val target = (sliderPosition * durationMs).toLong().coerceAtLeast(0L)
                onSeek(target)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun LandscapeTopBar(
    video: VideoItem?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableStateOf(formatClock(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            clock = formatClock(System.currentTimeMillis())
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LandscapeIconButton(onClick = onBack, size = 42.dp) {
                BackArrowIcon(tint = Color.White, size = 34.dp)
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = buildLandscapeTitle(video),
                color = ArcticColors.OnSurface,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = clock,
                color = ArcticColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            Text(
                text = "展开",
                color = ArcticColors.Primary.copy(alpha = 0.86f),
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            TVIcon(tint = Color.White, size = 30.dp)
            Spacer(Modifier.width(14.dp))
            ShareLandscapeIcon(tint = Color.White, size = 30.dp)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarDot()
            Spacer(Modifier.width(9.dp))
            Text(
                text = video?.author?.takeIf { it.isNotBlank() } ?: "作者",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(ArcticColors.PrimaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = ArcticColors.OnPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxScope.LandscapeSideControls() {
    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .padding(start = 22.dp, top = 86.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LandscapeCircleButton {
            SunIcon(tint = Color.White, size = 28.dp)
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(end = 22.dp, top = 86.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LandscapeCircleButton {
            LockIcon(tint = Color.White, size = 28.dp)
        }
        LandscapeCircleButton {
            VolumeIcon(tint = Color.White, size = 29.dp)
        }
    }
}

@Composable
private fun LandscapeCenterToggle(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(ArcticColors.Glass.copy(alpha = 0.26f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            PauseIcon(tint = Color.White.copy(alpha = 0.84f), size = 66.dp)
        } else {
            PlayIcon(tint = Color.White.copy(alpha = 0.84f), size = 66.dp)
        }
    }
}

@Composable
private fun LandscapeBottomControls(
    video: VideoItem?,
    positionMs: Long,
    durationMs: Long,
    sliderPosition: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.width(110.dp),
            )
            Text(
                text = "·  章节1：引言  ›  |  下一章",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Slider(
            value = sliderPosition.coerceIn(0f, 1f),
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = ArcticColors.Primary,
                activeTrackColor = ArcticColors.PrimaryContainer,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BottomAction(
                count = formatCount(video?.likes ?: 0),
                icon = { HeartFilledIcon(size = 30.dp, tint = Color.White) },
            )
            Spacer(Modifier.width(26.dp))
            BottomAction(
                count = formatCount(video?.comments ?: 0),
                icon = { CommentIcon(size = 30.dp, tint = Color.White) },
            )
            Spacer(Modifier.width(26.dp))
            BottomAction(
                count = formatCount(video?.collections ?: 0),
                icon = { BookmarkIcon(size = 31.dp, tint = Color.White) },
            )
            Spacer(Modifier.width(26.dp))
            BottomAction(
                count = "弹",
                icon = { Text("弹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .width(300.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(ArcticColors.Glass.copy(alpha = 0.48f))
                    .border(1.dp, ArcticColors.Outline.copy(alpha = 0.52f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "发一条友好的弹幕吧~",
                    color = Color.White.copy(alpha = 0.64f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Text("倍速", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp))
            MoreDotsIcon(tint = Color.White, size = 32.dp)
        }
    }
}

@Composable
private fun BottomAction(
    count: String,
    icon: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(
            text = count,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun LandscapeCircleButton(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ArcticColors.Glass.copy(alpha = 0.42f))
            .border(1.dp, ArcticColors.Outline.copy(alpha = 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LandscapeIconButton(
    onClick: () -> Unit,
    size: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AvatarDot() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFCBD5E1)),
        )
    }
}

@Composable
private fun BackArrowIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawLine(tint, Offset(s * 0.68f, s * 0.14f), Offset(s * 0.30f, s * 0.50f), s * 0.09f, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.30f, s * 0.50f), Offset(s * 0.68f, s * 0.86f), s * 0.09f, StrokeCap.Round)
    }
}

@Composable
private fun SunIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawCircle(tint, radius = s * 0.18f, center = Offset(s * 0.5f, s * 0.5f))
        repeat(8) { i ->
            val angle = Math.toRadians((i * 45).toDouble())
            val start = Offset(
                x = s * 0.5f + kotlin.math.cos(angle).toFloat() * s * 0.30f,
                y = s * 0.5f + kotlin.math.sin(angle).toFloat() * s * 0.30f,
            )
            val end = Offset(
                x = s * 0.5f + kotlin.math.cos(angle).toFloat() * s * 0.43f,
                y = s * 0.5f + kotlin.math.sin(angle).toFloat() * s * 0.43f,
            )
            drawLine(tint, start, end, s * 0.08f, StrokeCap.Round)
        }
    }
}

@Composable
private fun LockIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val stroke = s * 0.08f
        drawArc(
            color = tint,
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(s * 0.27f, s * 0.12f),
            size = Size(s * 0.46f, s * 0.50f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.22f, s * 0.43f),
            size = Size(s * 0.56f, s * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun VolumeIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val body = Path().apply {
            moveTo(s * 0.12f, s * 0.42f)
            lineTo(s * 0.30f, s * 0.42f)
            lineTo(s * 0.54f, s * 0.22f)
            lineTo(s * 0.54f, s * 0.78f)
            lineTo(s * 0.30f, s * 0.58f)
            lineTo(s * 0.12f, s * 0.58f)
            close()
        }
        drawPath(body, tint)
        drawArc(
            color = tint,
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(s * 0.48f, s * 0.30f),
            size = Size(s * 0.32f, s * 0.40f),
            style = Stroke(width = s * 0.08f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun TVIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val stroke = s * 0.08f
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.12f, s * 0.22f),
            size = Size(s * 0.76f, s * 0.54f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawLine(tint, Offset(s * 0.38f, s * 0.76f), Offset(s * 0.30f, s * 0.92f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.62f, s * 0.76f), Offset(s * 0.70f, s * 0.92f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.34f, s * 0.36f), Offset(s * 0.66f, s * 0.64f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.66f, s * 0.36f), Offset(s * 0.34f, s * 0.64f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ShareLandscapeIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val stroke = s * 0.09f
        drawLine(tint, Offset(s * 0.18f, s * 0.58f), Offset(s * 0.72f, s * 0.26f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.72f, s * 0.26f), Offset(s * 0.72f, s * 0.48f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.72f, s * 0.26f), Offset(s * 0.50f, s * 0.26f), stroke, StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = 190f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(s * 0.14f, s * 0.34f),
            size = Size(s * 0.60f, s * 0.50f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun MoreDotsIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.30f, s * 0.50f))
        drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.50f, s * 0.50f))
        drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.70f, s * 0.50f))
    }
}

private fun Long.validDuration(): Long = if (this > 0L && this < Long.MAX_VALUE / 2) this else 0L

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatClock(timestampMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
}

private fun buildLandscapeTitle(video: VideoItem?): String {
    val item = video ?: return ""
    val tags = item.tags.take(3).joinToString(" ") { "#$it" }
    val suffix = if (tags.isBlank()) "" else "  $tags"
    return "${item.title}$suffix"
}
