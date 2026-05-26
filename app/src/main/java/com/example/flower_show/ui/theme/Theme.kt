package com.example.flower_show.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark color scheme inspired by TikTok-Clone (dark_black background)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF2D55),        // TikTok-style red/pink accent
    background = Color(0xFF0D0D0D),     // dark_black
    surface = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    secondary = Color(0xFF25F4EE),      // Teal accent (like TikTok)
)

@Composable
fun FlowerShowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
