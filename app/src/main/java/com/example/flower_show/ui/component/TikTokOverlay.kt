package com.example.flower_show.ui.component

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.flower_show.model.VideoQuality
import com.example.flower_show.ui.theme.ArcticColors

private val TikTokPink = ArcticColors.PrimaryContainer
private val ActionLikeRed = Color(0xFFFF2D55)
private val ActionCollectGold = Color(0xFFFFC107)

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
                    color = ArcticColors.OnSurfaceVariant.copy(alpha = 0.68f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-1).dp)
                        .size(8.dp)
                        .background(ArcticColors.PrimaryContainer, CircleShape),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "For you",
                    color = ArcticColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(2.dp)
                        .background(ArcticColors.PrimaryContainer, RoundedCornerShape(2.dp)),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clip(CircleShape)
                .background(ArcticColors.Glass.copy(alpha = 0.24f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onSearchClick),
            contentAlignment = Alignment.Center,
        ) {
            SearchIcon(
                tint = ArcticColors.Primary,
                size = 24.dp,
            )
        }
    }
}

@Composable
fun TikTokBottomNavigationBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ArcticColors.Background.copy(alpha = 0.72f))
            .border(1.dp, ArcticColors.Outline.copy(alpha = 0.28f))
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
    availableQualities: List<VideoQuality> = emptyList(),
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
                if (isLiked) HeartFilledIcon(size = 42.dp, tint = ActionLikeRed, onClick = onLikeClick)
                else HeartOutlineIcon(size = 42.dp, tint = Color.White, onClick = onLikeClick)
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
                    tint = if (isCollected) ActionCollectGold else Color.White,
                    size = 42.dp,
                    filled = isCollected,
                    onClick = onCollectClick,
                )
            },
        )
        TikTokActionItem(
            count = if (shares > 0) formatCount(shares) else "分享",
            icon = { ShareIcon(size = 46.dp) },
        )

        // Quality selector — only visible when multi-quality data exists
        if (availableQualities.size > 1) {
            TikTokActionItem(
                count = currentQualityName ?: "画质",
                icon = {
                    Box {
                        Text(
                            "画质", color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .testTag("quality_button")
                                .clickable { showQualityMenu = true },
                        )
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false },
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.testTag("quality_option_auto"),
                                text = {
                                    val label = if (qualityMode == "Auto") "✓ 自动（当前 ${currentQualityName ?: "自动"}）"
                                    else "  自动"
                                    Text(label, color = Color.Black)
                                },
                                onClick = { showQualityMenu = false; onEnableAutoQuality() },
                            )
                            availableQualities.forEach { quality ->
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("quality_option_${quality.name}"),
                                    text = {
                                        val label = if (qualityMode == "Manual" && currentQualityName == quality.name)
                                            "✓ ${quality.name}" else "  ${quality.name}"
                                        Text(label, color = Color.Black)
                                    },
                                    onClick = {
                                        showQualityMenu = false
                                        onSetQuality(quality.name, quality.url)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        } else {
            TikTokActionItem(
                count = "画质",
                icon = {
                    Text(
                        "画质", color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("quality_button"),
                    )
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
                    .background(ArcticColors.Glass.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
                    .clickable(enabled = onSubtitleClick != null) {
                        onSubtitleClick?.invoke(subtitle)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    color = ArcticColors.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "A",
                    color = ArcticColors.PrimaryContainer.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = author,
            color = ArcticColors.OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = title,
            color = ArcticColors.OnSurface,
            fontSize = 18.sp,
            lineHeight = 23.sp,
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
            .background(ArcticColors.Glass.copy(alpha = 0.34f))
            .border(1.dp, ArcticColors.PrimaryContainer.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LIVE",
            color = ArcticColors.Primary,
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
            model = rememberFlowerImageRequest(
                data = avatarUrl,
                slot = FlowerImageSlot.Avatar,
            ),
            contentDescription = "头像",
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(1.5.dp, ArcticColors.Primary.copy(alpha = 0.92f), CircleShape),
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
                color = ArcticColors.OnPrimary,
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
            color = ArcticColors.OnSurface,
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
            .background(ArcticColors.SurfaceHigh.copy(alpha = 0.82f), CircleShape)
            .border(1.dp, ArcticColors.Outline.copy(alpha = 0.60f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = rememberFlowerImageRequest(
                data = avatarUrl,
                slot = FlowerImageSlot.DiscAvatar,
            ),
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
    val tint = if (selected) ArcticColors.Primary else ArcticColors.OnSurfaceVariant.copy(alpha = 0.64f)
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
                        color = ArcticColors.OnPrimary,
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
                .size(width = 54.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ArcticColors.Glass.copy(alpha = 0.62f))
                .border(1.dp, ArcticColors.PrimaryContainer.copy(alpha = 0.70f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = ArcticColors.Primary,
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
    Icon(
        imageVector = Icons.Filled.Home,
        contentDescription = "首页",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun FriendsNavIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Group,
        contentDescription = "朋友",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun InboxNavIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Mail,
        contentDescription = "消息",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun ProfileNavIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = "我的",
        tint = tint,
        modifier = Modifier.size(size),
    )
}
