package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.AuroraGradient
import com.example.flower_show.ui.theme.AuroraShapes
import com.example.flower_show.ui.theme.auroraGlass

object SocialDimens {
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp
    val avatarSmall = 52.dp
    val avatarLarge = 92.dp
    val touchTarget = 48.dp
}

@Composable
fun SocialTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    titleLeading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = SocialDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("social_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
        } else {
            Spacer(Modifier.width(SocialDimens.touchTarget))
        }
        if (titleLeading != null) {
            titleLeading()
            Spacer(Modifier.width(SocialDimens.spaceSm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.sizeIn(
                minWidth = SocialDimens.touchTarget,
                minHeight = SocialDimens.touchTarget,
            ),
            contentAlignment = Alignment.Center,
        ) {
            trailing?.invoke()
        }
    }
}

@Composable
fun SocialAvatar(
    profile: SocialProfile,
    modifier: Modifier = Modifier,
    size: Dp = SocialDimens.avatarSmall,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.nickname.take(1),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = "${profile.nickname}的头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun SocialSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "social_search_input",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        shape = AuroraShapes.Capsule,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun RelationUserRow(
    profile: SocialProfile,
    onProfileClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = SocialDimens.spaceMd, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(profile = profile)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.isMutual) {
                    Spacer(Modifier.width(SocialDimens.spaceSm))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "互关",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(SocialDimens.spaceXs))
            Text(
                text = "@${profile.account} · ${profile.bio}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(SocialDimens.spaceSm))
        FollowActionButton(
            isFollowing = profile.isFollowing,
            onClick = onFollowClick,
            modifier = Modifier.testTag(
                if (profile.isFollowing) "unfollow_${profile.id}" else "follow_${profile.id}",
            ),
        )
    }
}

@Composable
fun ProfileVideoTile(
    video: ProfileVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visibilityEditable: Boolean = false,
    onVisibilityChange: (Boolean) -> Unit = {},
) {
    Box(
        modifier = modifier
            .height(168.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                role = Role.Button
                contentDescription = "视频：${video.title}"
            }
            .testTag("profile_video_${video.id}")
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = video.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.56f))
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(SocialDimens.spaceXs))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!video.isVisibleOnProfile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "已隐藏",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (visibilityEditable) {
            IconButton(
                onClick = { onVisibilityChange(!video.isVisibleOnProfile) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("visibility_${video.id}"),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                ) {
                    Icon(
                        imageVector = if (video.isVisibleOnProfile) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        },
                        contentDescription = if (video.isVisibleOnProfile) "从主页隐藏" else "展示在主页",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FollowActionButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isFollowing) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = SocialDimens.touchTarget)
                .auroraGlass(shape = AuroraShapes.Capsule),
            shape = AuroraShapes.Capsule,
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("已关注")
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = SocialDimens.touchTarget)
                .clip(AuroraShapes.Capsule)
                .background(AuroraGradient, AuroraShapes.Capsule),
            shape = AuroraShapes.Capsule,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = ArcticColors.Background,
            ),
        ) {
            Text("关注")
        }
    }
}

fun formatSocialCount(value: Int): String = when {
    value >= 100_000_000 -> "${value / 100_000_000}亿"
    value >= 10_000 -> {
        val tenths = value / 1_000 % 10
        if (tenths == 0) "${value / 10_000}万" else "${value / 10_000}.${tenths}万"
    }
    else -> value.toString()
}
