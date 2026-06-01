package com.example.flower_show.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val TikTokPink = Color(0xFFFF2D55)
private val TikTokCyan = Color(0xFF25F4EE)

@Composable
fun TikTokTopNavigation(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 26.dp),
    ) {
        LiveBadge(modifier = Modifier.align(Alignment.CenterStart))

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Box {
                Text(
                    text = "Following",
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-1).dp)
                        .size(8.dp)
                        .background(TikTokPink, CircleShape),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "For you",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(3.dp)
                        .background(Color.White, RoundedCornerShape(2.dp)),
                )
            }
        }

        SearchIcon(
            tint = Color.White,
            size = 42.dp,
            onClick = onSearchClick,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
fun TikTokBottomNavigationBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TikTokNavItem(
            label = "Home",
            selected = true,
            icon = { tint -> HomeNavIcon(tint = tint, size = 32.dp) },
        )
        TikTokNavItem(
            label = "Friends",
            badge = "5",
            icon = { tint -> FriendsNavIcon(tint = tint, size = 32.dp) },
        )
        CreateNavButton()
        TikTokNavItem(
            label = "Inbox",
            badge = "23",
            icon = { tint -> InboxNavIcon(tint = tint, size = 32.dp) },
        )
        TikTokNavItem(
            label = "Profile",
            icon = { tint -> ProfileNavIcon(tint = tint, size = 32.dp) },
        )
    }
}

@Composable
fun TikTokActionRail(
    avatarUrl: String,
    isLiked: Boolean,
    isCollected: Boolean,
    likes: Int,
    comments: Int,
    collections: Int,
    shares: Int,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    qualityUrls: Map<String, String>? = null,
    qualityMode: String = "Auto",
    currentQualityName: String? = null,
    onSetQuality: (String, String) -> Unit = { _, _ -> },
    onEnableAutoQuality: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.width(68.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CreatorAvatar(avatarUrl = avatarUrl)

        TikTokActionItem(
            count = formatCount(if (isLiked) likes + 1 else likes),
            icon = {
                if (isLiked) HeartFilledIcon(size = 42.dp, onClick = onLikeClick)
                else HeartOutlineIcon(size = 42.dp, onClick = onLikeClick)
            },
        )
        TikTokActionItem(
            count = formatCount(comments),
            icon = { CommentIcon(size = 40.dp) },
        )
        TikTokActionItem(
            count = if (collections > 0) formatCount(if (isCollected) collections + 1 else collections) else "收藏",
            icon = {
                BookmarkIcon(
                    tint = if (isCollected) Color(0xFFFFD75A) else Color.White,
                    size = 42.dp, onClick = onCollectClick,
                )
            },
        )
        TikTokActionItem(
            count = if (shares > 0) formatCount(shares) else "分享",
            icon = { ShareIcon(size = 46.dp) },
        )

        // Quality selector — only visible when multi-quality data exists
        val urls = qualityUrls
        if (urls != null && urls.size > 1) {
            TikTokActionItem(
                count = currentQualityName ?: "画质",
                icon = {
                    Box {
                        Text(
                            "画质", color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showQualityMenu = true },
                        )
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    val label = if (qualityMode == "Auto") "✓ 自动（当前 ${currentQualityName ?: "自动"}）"
                                    else "  自动"
                                    Text(label, color = Color.Black)
                                },
                                onClick = { showQualityMenu = false; onEnableAutoQuality() },
                            )
                            urls.forEach { (name, url) ->
                                DropdownMenuItem(
                                    text = {
                                        val label = if (qualityMode == "Manual" && currentQualityName == name)
                                            "✓ $name" else "  $name"
                                        Text(label, color = Color.Black)
                                    },
                                    onClick = { showQualityMenu = false; onSetQuality(name, url) },
                                )
                            }
                        }
                    }
                },
            )
        }

        RotatingDisc(avatarUrl = avatarUrl)
    }
}

@Composable
fun TikTokCaptionPanel(
    author: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 330.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!subtitle.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(enabled = onSubtitleClick != null) {
                        onSubtitleClick?.invoke(subtitle)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "A",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = author,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .border(2.dp, Color.White, RoundedCornerShape(7.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun CreatorAvatar(
    avatarUrl: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(62.dp), contentAlignment = Alignment.TopCenter) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
            contentDescription = "头像",
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(28.dp)
                .background(TikTokPink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TikTokActionItem(
    count: String,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(5.dp))
        Text(
            text = count,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RotatingDisc(
    avatarUrl: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .background(Color(0xFF1B1B1B), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun TikTokNavItem(
    label: String,
    selected: Boolean = false,
    badge: String? = null,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) Color.White else Color.White.copy(alpha = 0.58f)
    Column(
        modifier = Modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            icon(tint)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 11.dp, y = (-7).dp)
                        .background(TikTokPink, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateNavButton() {
    Box(
        modifier = Modifier
            .width(66.dp)
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-5).dp)
                .size(width = 48.dp, height = 34.dp)
                .background(TikTokCyan, RoundedCornerShape(10.dp)),
        )
        Box(
            modifier = Modifier
                .offset(x = 5.dp)
                .size(width = 48.dp, height = 34.dp)
                .background(TikTokPink, RoundedCornerShape(10.dp)),
        )
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 36.dp)
                .background(Color.White, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = Color.Black,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HomeNavIcon(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val roof = Path().apply {
            moveTo(s * 0.12f, s * 0.48f)
            lineTo(s * 0.5f, s * 0.16f)
            lineTo(s * 0.88f, s * 0.48f)
            lineTo(s * 0.78f, s * 0.58f)
            lineTo(s * 0.78f, s * 0.88f)
            lineTo(s * 0.60f, s * 0.88f)
            lineTo(s * 0.60f, s * 0.64f)
            lineTo(s * 0.40f, s * 0.64f)
            lineTo(s * 0.40f, s * 0.88f)
            lineTo(s * 0.22f, s * 0.88f)
            lineTo(s * 0.22f, s * 0.58f)
            close()
        }
        drawPath(roof, tint)
    }
}

@Composable
private fun FriendsNavIcon(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawCircle(tint, radius = s * 0.15f, center = Offset(s * 0.38f, s * 0.34f), style = Stroke(width = s * 0.09f))
        drawCircle(tint, radius = s * 0.13f, center = Offset(s * 0.67f, s * 0.39f), style = Stroke(width = s * 0.08f))
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.15f, s * 0.47f),
            size = Size(s * 0.46f, s * 0.38f),
            style = Stroke(width = s * 0.09f, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(s * 0.50f, s * 0.53f),
            size = Size(s * 0.35f, s * 0.28f),
            style = Stroke(width = s * 0.08f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun InboxNavIcon(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val p = Path().apply {
            moveTo(s * 0.16f, s * 0.18f)
            lineTo(s * 0.84f, s * 0.18f)
            lineTo(s * 0.84f, s * 0.66f)
            lineTo(s * 0.56f, s * 0.66f)
            lineTo(s * 0.44f, s * 0.82f)
            lineTo(s * 0.44f, s * 0.66f)
            lineTo(s * 0.16f, s * 0.66f)
            close()
        }
        drawPath(p, tint, style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ProfileNavIcon(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        drawCircle(tint, radius = s * 0.16f, center = Offset(s * 0.5f, s * 0.32f), style = Stroke(width = s * 0.09f))
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.20f, s * 0.48f),
            size = Size(s * 0.60f, s * 0.45f),
            style = Stroke(width = s * 0.09f, cap = StrokeCap.Round),
        )
    }
}
