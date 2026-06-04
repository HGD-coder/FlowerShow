package com.example.flower_show.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flower_show.model.VideoItem
import com.example.flower_show.player.VideoPlayerManager
import com.example.flower_show.ui.component.LandscapeVideoControls
import com.example.flower_show.ui.component.SearchIcon
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.FlowerShowTheme

@Preview(
    name = "\u641c\u7d22\u4e2d\u95f4\u9875 / Phone",
    group = "Search",
    showSystemUi = true,
    device = "spec:width=393dp,height=852dp,dpi=440",
)
@Composable
private fun SearchMiddlePreviewPhone() {
    FlowerShowTheme {
        SearchMiddlePreviewContent()
    }
}

@Preview(
    name = "\u641c\u7d22\u7ed3\u679c\u9875 / Phone",
    group = "Search",
    showSystemUi = true,
    device = "spec:width=393dp,height=852dp,dpi=440",
)
@Composable
private fun SearchResultPreviewPhone() {
    FlowerShowTheme {
        SearchResultPreviewContent()
    }
}

@Preview(
    name = "\u6a2a\u5c4f\u64ad\u653e\u5668 / Landscape",
    group = "Player",
    device = "spec:width=852dp,height=393dp,dpi=440",
)
@Composable
private fun LandscapePlayerPreview() {
    FlowerShowTheme {
        LandscapePlayerPreviewContent()
    }
}

@Composable
private fun SearchMiddlePreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        SearchPreviewTopBar(
            input = "\u535a\u4eba\u4f20",
            placeholder = "\u641c\u7d22\u7cbe\u5f69\u5185\u5bb9",
            dark = true,
        )

        Spacer(Modifier.height(24.dp))

        KeywordPreviewSection(
            title = "\u5386\u53f2\u8bb0\u5f55",
            keywords = listOf(
                "\u5c0f\u5c0f\u5fae\u5149",
                "\u4e1c\u4eac\u4e0b\u5c0f\u96e8",
                "\u8463\u5929\u5b9d",
                "\u8001\u4e61\u9e21",
                "\u8001\u4e61\u9e21\u514d\u8d39\u996e\u6599",
                "\u8272\u5f31\u6d4b\u8bd5\u56fe",
            ),
            action = "\u5c55\u5f00  v   |   \u6e05\u7a7a",
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ArcticColors.Outline.copy(alpha = 0.55f)),
        )

        KeywordPreviewSection(
            title = "\u731c\u4f60\u60f3\u641c",
            keywords = listOf(
                "\u535a\u4eba\u8de8\u65f6\u7a7a\u6551\u63f4",
                "\u5973\u6b66\u795em80",
                "\u706b\u5f71\u5fcd\u8005\u5267\u573a\u7248",
                "\u9e23\u4eba\u4f50\u52a9\u5408\u4f53",
                "\u4f50\u826f\u5a1c\u6218\u6597",
                "\u5ddd\u6728\u5267\u60c5",
                "\u5fcd\u8005\u4e16\u754c\u89e3\u8bf4",
                "\u535a\u4eba\u4f20\u540d\u573a\u9762",
            ),
            action = "\u6362\u4e00\u6362   |   ...",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchPreviewTopBar(
    input: String,
    placeholder: String,
    dark: Boolean,
) {
    val foreground = if (dark) ArcticColors.OnSurface else Color(0xFF202127)
    val muted = if (dark) ArcticColors.Muted else Color(0xFF8A8D95)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        BackChevronIcon(tint = foreground, size = 30.dp)
        Spacer(Modifier.width(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(if (dark) 8.dp else 14.dp))
                .background(if (dark) ArcticColors.Surface.copy(alpha = 0.92f) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (dark) ArcticColors.Outline.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(if (dark) 8.dp else 14.dp),
                )
                .padding(start = 18.dp, end = 8.dp),
        ) {
            SearchIcon(tint = muted, size = 21.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = input.ifBlank { placeholder },
                color = if (input.isBlank()) muted else foreground,
                fontSize = if (dark) 17.sp else 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .width(1.dp)
                    .background(if (dark) ArcticColors.Outline.copy(alpha = 0.60f) else Color(0xFFE8E8EB)),
            )
            Text(
                text = "\u641c\u7d22",
                color = if (dark) ArcticColors.Primary else foreground,
                fontSize = if (dark) 16.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(if (dark) 62.dp else 70.dp),
            )
        }
    }
}

@Composable
private fun KeywordPreviewSection(
    title: String,
    keywords: List<String>,
    action: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Text(
                text = title,
                color = ArcticColors.Muted,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = action,
                color = ArcticColors.Muted,
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            keywords.chunked(2).forEach { rowKeywords ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowKeywords.forEach { keyword ->
                        Text(
                            text = keyword,
                            color = ArcticColors.OnSurface,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 34.dp),
                        )
                    }
                    if (rowKeywords.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultPreviewContent() {
    val videos = remember { previewVideos() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        SearchPreviewTopBar(
            input = "\u535a\u4eba\u8de8\u65f6\u7a7a\u6551\u63f4",
            placeholder = "\u641c\u7d22",
            dark = true,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            listOf(
                "\u7efc\u5408",
                "\u89c6\u9891",
                "\u7528\u6237",
                "\u76f8\u5173\u641c\u7d22",
            ).forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (index == 0) ArcticColors.PrimaryContainer else ArcticColors.Surface)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (index == 0) ArcticColors.OnPrimary else ArcticColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(videos) { item ->
                SearchResultPreviewRow(video = item)
            }
        }
    }
}

@Composable
private fun SearchResultPreviewRow(video: VideoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 130.dp, height = 78.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ArcticColors.SurfaceHigh,
                            ArcticColors.PrimaryGlow.copy(alpha = 0.62f),
                            ArcticColors.BackgroundDeep,
                        ),
                    ),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.35f,
                    center = Offset(size.width * 0.78f, size.height * 0.30f),
                )
            }
            PlayTriangleIcon(
                tint = Color.White,
                size = 24.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = ArcticColors.OnSurface,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "@${video.author}",
                color = ArcticColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${formatCountPreview(video.likes)}\u8d5e  \u00b7  ${formatCountPreview(video.comments)}\u8bc4\u8bba",
                color = ArcticColors.Muted.copy(alpha = 0.82f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LandscapePlayerPreviewContent() {
    val context = LocalContext.current
    val playerManager = remember { VideoPlayerManager(context) }
    val video = remember { previewLandscapeVideo() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ArcticColors.BackgroundDeep,
                            ArcticColors.SurfaceHigh,
                            ArcticColors.Background,
                        ),
                    ),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.34f),
                    topLeft = Offset(size.width * 0.15f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.28f),
                    cornerRadius = CornerRadius(14f, 14f),
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.34f),
                    topLeft = Offset(size.width * 0.66f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.30f),
                    cornerRadius = CornerRadius(14f, 14f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.24f,
                    center = Offset(size.width * 0.50f, size.height * 0.46f),
                )
            }

            Text(
                text = video.title,
                color = ArcticColors.OnSurface.copy(alpha = 0.40f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 128.dp),
            )
        }

        LandscapeVideoControls(
            video = video,
            playerManager = playerManager,
            visible = true,
            onToggleVisible = {},
            onBack = {},
            onSeek = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BackChevronIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawLine(
            color = tint,
            start = Offset(s * 0.68f, s * 0.14f),
            end = Offset(s * 0.30f, s * 0.50f),
            strokeWidth = s * 0.10f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(s * 0.30f, s * 0.50f),
            end = Offset(s * 0.68f, s * 0.86f),
            strokeWidth = s * 0.10f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PlayTriangleIcon(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val path = Path().apply {
            moveTo(s * 0.34f, s * 0.22f)
            lineTo(s * 0.34f, s * 0.78f)
            lineTo(s * 0.78f, s * 0.50f)
            close()
        }
        drawPath(path, tint)
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = s * 0.48f,
            center = Offset(s * 0.50f, s * 0.50f),
            style = Stroke(width = s * 0.04f),
        )
    }
}

private fun previewVideos(): List<VideoItem> {
    return listOf(
        previewVideo(
            id = "preview-result-001",
            title = "\u4f60\u535a\u4eba\u53d4\u53d4\u73b0\u5728\u582a\u6bd4\u4e00\u4e2a\u8499\u591a\uff0c\u8c01\u8fd8\u6709\u68a6\u60f3\uff01",
            author = "\u5361\u6148sama",
            likes = 17000,
            comments = 937,
        ),
        previewVideo(
            id = "preview-result-002",
            title = "\u535a\u4eba\u8de8\u65f6\u7a7a\u6551\u63f4\uff1a\u9e23\u4eba\u548c\u4f50\u52a9\u7684\u540d\u573a\u9762",
            author = "\u5fcd\u8005\u89e3\u8bf4\u5458",
            likes = 81000,
            comments = 2460,
        ),
        previewVideo(
            id = "preview-result-003",
            title = "\u4e00\u53e3\u6c14\u770b\u5b8c\u65b0\u4e16\u4ee3\u5fcd\u8005\u7684\u6210\u957f\u7ebf",
            author = "\u52a8\u753b\u526a\u8f91\u6240",
            likes = 19000,
            comments = 420,
        ),
        previewVideo(
            id = "preview-result-004",
            title = "\u9e23\u4eba\u4f50\u52a9\u518d\u8054\u624b\uff0c\u8fd9\u6bb5\u6218\u6597\u771f\u7684\u71c3",
            author = "\u70ed\u8840\u756a\u5267",
            likes = 54000,
            comments = 1280,
        ),
    )
}

private fun previewLandscapeVideo(): VideoItem {
    return previewVideo(
        id = "preview-landscape-001",
        title = "ClaudeCode\u548cCodex\u5230\u5e95\u9009\u54ea\u4e2a\uff1f",
        author = "Josh\u7684AI\u7b14\u8bb0",
        likes = 6022,
        comments = 259,
        collections = 4586,
        tags = listOf("AI\u65b0\u661f\u8ba1\u5212", "\u9752\u5e74\u521b\u4f5c\u8005\u6210\u957f\u8ba1\u5212", "ClaudeCode"),
    )
}

private fun previewVideo(
    id: String,
    title: String,
    author: String,
    likes: Int,
    comments: Int,
    collections: Int = likes / 2,
    tags: List<String> = emptyList(),
): VideoItem {
    return VideoItem(
        id = id,
        title = title,
        author = author,
        avatarUrl = "",
        videoUrl = "",
        coverUrl = "",
        likes = likes,
        comments = comments,
        collections = collections,
        shares = likes / 3,
        tags = tags,
        recommendWords = listOf(
            "\u76f8\u5173\u89e3\u8bf4",
            "\u9ad8\u80fd\u526a\u8f91",
            "\u5267\u60c5\u590d\u76d8",
        ),
        qualityUrls = mapOf("720p" to ""),
    )
}

private fun formatCountPreview(value: Int): String {
    return if (value >= 10000) {
        val major = value / 10000
        val minor = (value % 10000) / 1000
        if (minor == 0) "$major\u4e07" else "$major.$minor\u4e07"
    } else {
        value.toString()
    }
}
