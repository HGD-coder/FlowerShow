package com.example.flower_show.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = ArcticColors.AuroraCyan,
    onPrimary = ArcticColors.Background,
    primaryContainer = ArcticColors.AuroraIndigo,
    onPrimaryContainer = ArcticColors.TextPrimary,
    secondary = ArcticColors.AuroraIndigo,
    onSecondary = ArcticColors.TextPrimary,
    secondaryContainer = ArcticColors.SurfaceHigh,
    onSecondaryContainer = ArcticColors.TextPrimary,
    tertiary = ArcticColors.AuroraPink,
    onTertiary = ArcticColors.Background,
    background = ArcticColors.Background,
    onBackground = ArcticColors.TextPrimary,
    surface = ArcticColors.Surface,
    surfaceVariant = ArcticColors.SurfaceHighest,
    surfaceContainer = ArcticColors.SurfaceLow,
    surfaceContainerHigh = ArcticColors.SurfaceHigh,
    onSurface = ArcticColors.TextPrimary,
    onSurfaceVariant = ArcticColors.TextSecondary,
    outline = ArcticColors.Outline,
    outlineVariant = ArcticColors.OutlineSoft,
)

private val AuroraTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    displayMedium = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleSmall = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val AuroraMaterialShapes = Shapes(
    extraSmall = AuroraShapes.Small,
    small = AuroraShapes.Small,
    medium = AuroraShapes.Medium,
    large = AuroraShapes.Large,
    extraLarge = AuroraShapes.Large,
)

@Composable
fun FlowerShowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AuroraTypography,
        shapes = AuroraMaterialShapes,
        content = content,
    )
}
