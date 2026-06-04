package com.example.flower_show.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Cold cinematic palette inspired by the Arctic Cinematics reference.
private val DarkColors = darkColorScheme(
    primary = ArcticColors.Primary,
    primaryContainer = ArcticColors.PrimaryContainer,
    onPrimary = ArcticColors.OnPrimary,
    background = ArcticColors.Background,
    onBackground = ArcticColors.OnSurface,
    surface = ArcticColors.Surface,
    surfaceVariant = ArcticColors.SurfaceHighest,
    onSurface = ArcticColors.OnSurface,
    onSurfaceVariant = ArcticColors.OnSurfaceVariant,
    secondary = ArcticColors.Muted,
    outline = ArcticColors.Outline,
)

@Composable
fun FlowerShowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
