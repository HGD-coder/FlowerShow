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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.flower_show.model.VideoQuality
import com.example.flower_show.model.VideoQualitySelector
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.AuroraGradient
import com.example.flower_show.ui.theme.AuroraShapes
import com.example.flower_show.ui.theme.CollectGradient
import com.example.flower_show.ui.theme.LikeGradient
import com.example.flower_show.ui.theme.auroraGlass
import com.example.flower_show.ui.theme.auroraGradient
import com.example.flower_show.ui.theme.gradientForeground
import com.example.flower_show.ui.theme.radialGlow
import com.example.flower_show.viewmodel.FeedKind

private val VideoFeedAccent = ArcticColors.PrimaryContainer

@Composable
fun VideoFeedTopBar(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedFeed: FeedKind = FeedKind.Recommended,
    onFeedSelected: (FeedKind) -> Unit = {},
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
            FeedTab(
                text = "关注",
                selected = selectedFeed == FeedKind.Following,
                testTag = "feed_tab_following",
                onClick = { onFeedSelected(FeedKind.Following) },
            )
            FeedTab(
                text = "推荐",
                selected = selectedFeed == FeedKind.Recommended,
                testTag = "feed_tab_recommended",
                onClick = { onFeedSelected(FeedKind.Recommended) },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .testTag("home_search_button")
                .auroraGlass(CircleShape)
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
private fun FeedTab(
    text: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag(testTag)
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.gradientForeground(),
            )
        } else {
            Text(
                text = text,
                color = ArcticColors.TextTertiary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .then(
                    if (selected) {
                        Modifier.background(AuroraGradient, AuroraShapes.Capsule)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
fun VideoFeedBottomBar(
    modifier: Modifier = Modifier,
    onFriendsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .auroraGlass(RectangleShape)
            .navigationBarsPadding()
            .padding(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        VideoFeedNavItem(
            selected = true,
            icon = { tint -> HomeNavIcon(tint = tint, size = 32.dp) },
            testTag = "bottom_home_button",
        )
        VideoFeedNavItem(
            icon = { tint -> FriendsNavIcon(tint = tint, size = 32.dp) },
            onClick = onFriendsClick,
            testTag = "bottom_friends_button",
        )
        CreateNavButton(onClick = onCreateClick)
        VideoFeedNavItem(
            icon = { tint -> InboxNavIcon(tint = tint, size = 32.dp) },
            onClick = onMessagesClick,
            testTag = "bottom_messages_button",
        )
        VideoFeedNavItem(
            icon = { tint -> ProfileNavIcon(tint = tint, size = 32.dp) },
            onClick = onProfileClick,
            testTag = "bottom_profile_button",
        )
    }
}

@Composable
fun VideoActionRail(
    avatarUrl: String,
    isLiked: Boolean,
    isCollected: Boolean,
    likes: Int,
    comments: Int,
    collections: Int,
    shares: Int,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onCommentClick: (() -> Unit)? = null,
    commentButtonTestTag: String? = null,
    onCreatorClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
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
        CreatorAvatar(
            avatarUrl = avatarUrl,
            onClick = onCreatorClick,
        )

        VideoActionItem(
            count = formatCount(likes),
            icon = {
                if (isLiked) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .radialGlow(ArcticColors.AuroraCyan),
                        contentAlignment = Alignment.Center,
                    ) {
                        HeartFilledIcon(
                            modifier = Modifier.gradientForeground(LikeGradient),
                            size = 42.dp,
                            tint = Color.White,
                            onClick = onLikeClick,
                        )
                    }
                } else {
                    HeartOutlineIcon(size = 42.dp, tint = Color.White, onClick = onLikeClick)
                }
            },
        )

        if (onShareClick != null) {
            VideoActionItem(
                count = if (shares > 0) formatCount(shares) else "分享",
                icon = {
                    ShareIcon(
                        modifier = Modifier.testTag("share_video_button"),
                        size = 48.dp,
                        onClick = onShareClick,
                    )
                },
            )
        }
        VideoActionItem(
            count = formatCount(comments),
            icon = {
                CommentIcon(
                    modifier = if (commentButtonTestTag != null) {
                        Modifier.testTag(commentButtonTestTag)
                    } else {
                        Modifier
                    },
                    size = 40.dp,
                    onClick = onCommentClick,
                )
            },
        )
        VideoActionItem(
            count = if (collections > 0) formatCount(collections) else "收藏",
            icon = {
                if (isCollected) {
                    BookmarkIcon(
                        modifier = Modifier.gradientForeground(CollectGradient),
                        tint = Color.White,
                        size = 42.dp,
                        filled = true,
                        onClick = onCollectClick,
                    )
                } else {
                    BookmarkIcon(
                        tint = Color.White,
                        size = 42.dp,
                        filled = false,
                        onClick = onCollectClick,
                    )
                }
            },
        )

        // Quality selector — only visible when multi-quality data exists
        if (availableQualities.size > 1) {
            VideoActionItem(
                count = VideoQualitySelector.qualityNameForDisplay(currentQualityName) ?: "画质",
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
                            containerColor = ArcticColors.SurfaceHigh,
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.testTag("quality_option_auto"),
                                text = {
                                    val currentQualityLabel =
                                        VideoQualitySelector.qualityNameForDisplay(currentQualityName) ?: "自动"
                                    val label = if (qualityMode == "Auto") "✓ 自动（当前 $currentQualityLabel）"
                                    else "  自动"
                                    Text(label, color = Color.White)
                                },
                                onClick = { showQualityMenu = false; onEnableAutoQuality() },
                            )
                            availableQualities.forEach { quality ->
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("quality_option_${quality.name}"),
                                    text = {
                                        val qualityLabel =
                                            VideoQualitySelector.qualityNameForDisplay(quality.name) ?: quality.name
                                        val label = if (qualityMode == "Manual" && currentQualityName == quality.name)
                                            "✓ $qualityLabel" else "  $qualityLabel"
                                        Text(label, color = Color.White)
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
            VideoActionItem(
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

    }
}

@Composable
fun VideoCaptionPanel(
    author: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    var canExpand by remember(title) { mutableStateOf(false) }

    Column(
        modifier = modifier.widthIn(max = 330.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!subtitle.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .auroraGlass(AuroraShapes.Small)
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
            maxLines = if (expanded) 12 else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    canExpand = result.hasVisualOverflow
                }
            },
        )
        if (canExpand || expanded) {
            Text(
                text = if (expanded) "\u6536\u8d77" else "\u5c55\u5f00",
                color = ArcticColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .auroraGlass(AuroraShapes.Small)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "直播",
            color = ArcticColors.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun CreatorAvatar(
    avatarUrl: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(62.dp)
            .testTag("creator_profile_button")
            .clip(CircleShape)
            .clickable(enabled = onClick != null, onClick = onClick ?: {}),
        contentAlignment = Alignment.TopCenter,
    ) {
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
                .background(VideoFeedAccent, CircleShape),
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
private fun VideoActionItem(
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
private fun VideoFeedNavItem(
    selected: Boolean = false,
    badge: String? = null,
    icon: @Composable (Color) -> Unit,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    Box(
        modifier = Modifier
            .width(58.dp)
            .sizeIn(minHeight = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(enabled = onClick != null, onClick = onClick ?: {}),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 44.dp)
                .clip(AuroraShapes.Capsule)
                .then(
                    if (selected) {
                        Modifier.background(auroraGradient(alpha = 0.20f), AuroraShapes.Capsule)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = if (selected) Modifier.gradientForeground() else Modifier,
            ) {
                icon(if (selected) Color.White else ArcticColors.TextTertiary)
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 11.dp, y = (-7).dp)
                        .background(VideoFeedAccent, CircleShape)
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
    }
}

@Composable
private fun CreateNavButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(66.dp)
            .height(56.dp)
            .testTag("bottom_create_button")
            .semantics {
                role = Role.Button
                contentDescription = "创作"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .blur(
                    radius = 24.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(ArcticColors.AuroraCyan.copy(alpha = 0.35f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AuroraGradient, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = ArcticColors.TextPrimary,
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
