package com.example.flower_show.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

val AuroraGradient: Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to ArcticColors.AuroraCyan,
        0.5f to ArcticColors.AuroraIndigo,
        1f to ArcticColors.AuroraPink,
    ),
    start = Offset(Float.POSITIVE_INFINITY, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY),
)

fun auroraGradient(alpha: Float = 1f): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to ArcticColors.AuroraCyan.copy(alpha = alpha),
        0.5f to ArcticColors.AuroraIndigo.copy(alpha = alpha),
        1f to ArcticColors.AuroraPink.copy(alpha = alpha),
    ),
    start = Offset(Float.POSITIVE_INFINITY, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY),
)

val LikeGradient: Brush = Brush.linearGradient(
    colors = listOf(ArcticColors.AuroraCyan, ArcticColors.AuroraPink),
)

val CollectGradient: Brush = Brush.linearGradient(
    colors = listOf(ArcticColors.AuroraCyan, ArcticColors.AuroraGreen),
)

object AuroraShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val Capsule = RoundedCornerShape(999.dp)
}

object AuroraGlass {
    val Background = Color.White.copy(alpha = 0.08f)
    const val BorderAlpha = 0.30f
    val TopHighlight = Color.White.copy(alpha = 0.12f)
}

fun Modifier.auroraGlass(
    shape: Shape = AuroraShapes.Medium,
    backgroundColor: Color = AuroraGlass.Background,
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .drawWithCache {
        val y = 0.5.dp.toPx()
        onDrawWithContent {
            drawContent()
            drawLine(
                color = AuroraGlass.TopHighlight,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
    .border(1.dp, auroraGradient(AuroraGlass.BorderAlpha), shape)

fun Modifier.gradientForeground(brush: Brush = AuroraGradient): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(
                brush = brush,
                blendMode = BlendMode.SrcIn,
            )
        }
    }

fun Modifier.radialGlow(
    color: Color,
    alpha: Float = 0.36f,
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.30f),
                Color.Transparent,
            ),
            center = center,
            radius = size.minDimension * 0.68f,
        ),
        radius = size.minDimension * 0.68f,
    )
}
