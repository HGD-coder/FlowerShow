package com.example.flower_show.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Compose icons — no XML, no external library.
 * 纯 Compose 矢量图标 — 无 XML、无三方库依赖。
 *
 * All icons are 24dp viewport, stroke style (2dp width), round caps/joins.
 * 所有图标基于 24dp 视口，2dp 描边，圆头/圆角。
 */

// ── Heart Outline (点赞空心) ──
@Composable
fun HeartOutlineIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.5f, s * 0.88f)
            cubicTo(s * 0.05f, s * 0.55f, s * 0.05f, s * 0.18f, s * 0.25f, s * 0.10f)
            cubicTo(s * 0.38f, s * 0.05f, s * 0.50f, s * 0.18f, s * 0.50f, s * 0.30f)
            cubicTo(s * 0.50f, s * 0.18f, s * 0.62f, s * 0.05f, s * 0.75f, s * 0.10f)
            cubicTo(s * 0.95f, s * 0.18f, s * 0.95f, s * 0.55f, s * 0.50f, s * 0.88f)
            close()
        }
        drawPath(p, tint, style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Heart Filled (点赞实心) ──
@Composable
fun HeartFilledIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFFF2D55),
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.5f, s * 0.88f)
            cubicTo(s * 0.05f, s * 0.55f, s * 0.05f, s * 0.18f, s * 0.25f, s * 0.10f)
            cubicTo(s * 0.38f, s * 0.05f, s * 0.50f, s * 0.18f, s * 0.50f, s * 0.30f)
            cubicTo(s * 0.50f, s * 0.18f, s * 0.62f, s * 0.05f, s * 0.75f, s * 0.10f)
            cubicTo(s * 0.95f, s * 0.18f, s * 0.95f, s * 0.55f, s * 0.50f, s * 0.88f)
            close()
        }
        drawPath(p, tint)
    }
}

// ── Comment (评论气泡) ──
@Composable
fun CommentIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.88f, s * 0.08f)
            cubicTo(s * 0.95f, s * 0.08f, s * 1.00f, s * 0.13f, s * 1.00f, s * 0.20f)
            lineTo(s * 1.00f, s * 0.63f)
            cubicTo(s * 1.00f, s * 0.69f, s * 0.95f, s * 0.75f, s * 0.88f, s * 0.75f)
            lineTo(s * 0.30f, s * 0.75f)
            lineTo(s * 0.05f, s * 0.95f)
            lineTo(s * 0.05f, s * 0.20f)
            cubicTo(s * 0.05f, s * 0.13f, s * 0.10f, s * 0.08f, s * 0.17f, s * 0.08f)
            close()
        }
        drawPath(p, tint, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Bookmark (收藏书签) ──
@Composable
fun BookmarkIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.79f, s * 0.04f)
            cubicTo(s * 0.85f, s * 0.04f, s * 0.88f, s * 0.08f, s * 0.88f, s * 0.13f)
            lineTo(s * 0.88f, s * 0.96f)
            lineTo(s * 0.50f, s * 0.75f)
            lineTo(s * 0.12f, s * 0.96f)
            lineTo(s * 0.12f, s * 0.13f)
            cubicTo(s * 0.12f, s * 0.08f, s * 0.15f, s * 0.04f, s * 0.21f, s * 0.04f)
            close()
        }
        drawPath(p, tint, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Share (分享箭头) ──
@Composable
fun ShareIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        // Arrow body
        drawLine(tint, Offset(s * 0.17f, s * 0.50f), Offset(s * 0.83f, s * 0.50f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        // Arrow head top
        drawLine(tint, Offset(s * 0.83f, s * 0.50f), Offset(s * 0.67f, s * 0.29f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        // Arrow head bottom
        drawLine(tint, Offset(s * 0.83f, s * 0.50f), Offset(s * 0.67f, s * 0.71f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        // Vert line top
        drawLine(tint, Offset(s * 0.50f, s * 0.50f), Offset(s * 0.50f, s * 0.08f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        // Vert line bottom
        drawLine(tint, Offset(s * 0.50f, s * 0.50f), Offset(s * 0.50f, s * 0.92f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}

// ── Play (播放三角) ──
@Composable
fun PlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.22f, s * 0.10f)
            lineTo(s * 0.88f, s * 0.50f)
            lineTo(s * 0.22f, s * 0.90f)
            close()
        }
        drawPath(p, tint)
    }
}

// ── Pause (暂停双竖线) ──
@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        val barW = s * 0.22f
        val barH = s * 0.72f
        val top = (s - barH) / 2f
        drawRect(tint, topLeft = Offset(s * 0.18f, top), size = Size(barW, barH))
        drawRect(tint, topLeft = Offset(s * 0.60f, top), size = Size(barW, barH))
    }
}

// ── Search (搜索放大镜) ──
@Composable
fun SearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        drawCircle(tint, radius = s * 0.31f, center = Offset(s * 0.38f, s * 0.38f),
            style = Stroke(width = s * 0.10f, cap = StrokeCap.Round))
        drawLine(tint, Offset(s * 0.60f, s * 0.60f), Offset(s * 0.88f, s * 0.88f),
            strokeWidth = s * 0.10f, cap = StrokeCap.Round)
    }
}

// ── Close/Clear (关闭/清除) ──
@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Canvas(modifier = m.then(Modifier.size(size))) {
        val s = this.size.width
        drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), strokeWidth = s * 0.10f, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.8f, s * 0.2f), strokeWidth = s * 0.10f, cap = StrokeCap.Round)
    }
}
