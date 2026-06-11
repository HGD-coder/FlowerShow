package com.example.flower_show.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.ui.component.VideoFeedBottomBar
import com.example.flower_show.ui.component.VideoFeedTopBar
import com.example.flower_show.ui.component.VideoCard
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.FlowerShowTheme

@Preview(
    name = "主页 / Phone",
    group = "Home",
    showSystemUi = true,
    device = "spec:width=393dp,height=852dp,dpi=440",
)
@Composable
private fun HomePreviewPhone() {
    FlowerShowTheme {
        HomePreviewContent()
    }
}

@Preview(
    name = "主页 / Small Phone",
    group = "Home",
    showSystemUi = true,
    device = "spec:width=360dp,height=740dp,dpi=420",
)
@Composable
private fun HomePreviewSmallPhone() {
    FlowerShowTheme {
        HomePreviewContent()
    }
}

@Preview(
    name = "主页 / Landscape",
    group = "Home",
    device = "spec:width=852dp,height=393dp,dpi=440",
)
@Composable
private fun HomePreviewLandscape() {
    FlowerShowTheme {
        HomePreviewContent()
    }
}

@Composable
private fun HomePreviewContent() {
    val context = LocalContext.current
    val playerManager = remember { VideoPlayerManager(context) }
    val video = remember { previewHomeVideo() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background),
    ) {
        VideoCard(
            video = video,
            playerManager = playerManager,
            playerContent = {
                PreviewVideoBackdrop(
                    title = video.title,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            onSeek = {},
            onRecommendWordClick = {},
            onSetQuality = { _, _ -> },
            onEnableAutoQuality = {},
            onToggleFullscreen = {},
            qualityMode = "Auto",
            currentQualityName = "720p",
            modifier = Modifier.fillMaxSize(),
        )

        VideoFeedTopBar(
            onSearchClick = {},
            modifier = Modifier.align(Alignment.TopCenter),
        )

        VideoFeedBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BoxScope.PreviewVideoBackdrop(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ArcticColors.BackgroundDeep,
                        ArcticColors.SurfaceHigh,
                        ArcticColors.Background,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ArcticColors.PrimaryContainer.copy(alpha = 0.24f),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.18f, size.height * 0.28f),
            )
            drawCircle(
                color = ArcticColors.NeonCyan.copy(alpha = 0.22f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.88f, size.height * 0.48f),
            )
        }

        Text(
            text = title,
            color = ArcticColors.OnSurface.copy(alpha = 0.88f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 36.dp),
        )
    }
}

private fun previewHomeVideo(): VideoItem {
    return VideoItem(
        id = "preview-home-001",
        title = "ClaudeCode和Codex到底选哪个？",
        author = "Josh的AI笔记",
        avatarUrl = "",
        videoUrl = "",
        coverUrl = "",
        likes = 6022,
        comments = 259,
        collections = 4586,
        shares = 10000,
        tags = listOf("AI新星计划", "青年创作者成长计划", "ClaudeCode", "Codex"),
        recommendWords = listOf(
            "ClaudeCode和Codex对比",
            "AI编程工具选择",
            "Codex实战体验",
            "Claude Code成本",
        ),
        qualityUrls = mapOf(
            "480p" to "",
            "720p" to "",
        ),
    )
}
