package com.example.flower_show.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.flower_show.model.VideoComment
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.AuroraGradient
import com.example.flower_show.ui.theme.AuroraShapes
import com.example.flower_show.ui.theme.auroraGlass
import com.example.flower_show.viewmodel.CommentState

@Composable
fun CommentOverlay(
    state: CommentState,
    onDismiss: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("comment_scrim")
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = state.visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 500f,
                ),
            ) + fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 180),
            ) + fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            CommentSheet(
                state = state,
                onDismiss = onDismiss,
                onDraftChanged = onDraftChanged,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun CommentSheet(
    state: CommentState,
    onDismiss: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.74f)
            .testTag("comment_sheet")
            .auroraGlass(
                shape = sheetShape,
                backgroundColor = ArcticColors.Surface.copy(alpha = 0.92f),
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            },
        shape = sheetShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SocialDimens.spaceMd,
                        top = SocialDimens.spaceSm,
                        end = SocialDimens.spaceSm,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = SocialDimens.spaceXs)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Text(
                    text = "${state.totalCount} 条评论",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = SocialDimens.spaceLg),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("comment_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭评论",
                    )
                }
            }

            if (state.comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("comment_list"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isLoading) "评论加载中…" else "还没有评论，来聊聊吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("comment_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = SocialDimens.spaceMd,
                        vertical = SocialDimens.spaceMd,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SocialDimens.spaceMd),
                ) {
                    items(
                        items = state.comments,
                        key = VideoComment::id,
                    ) { comment ->
                        CommentRow(comment = comment)
                    }
                }
            }

            CommentComposer(
                draft = state.draft,
                submitting = state.isSubmitting,
                onDraftChanged = onDraftChanged,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun CommentRow(
    comment: VideoComment,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        CommentAvatar(comment = comment)
        Spacer(Modifier.width(SocialDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    color = if (comment.isMine) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(SocialDimens.spaceSm))
                Text(
                    text = comment.timeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(SocialDimens.spaceXs))
            Text(
                text = comment.content,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CommentAvatar(
    comment: VideoComment,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (comment.isMine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = comment.author.firstOrNull()?.toString().orEmpty(),
            color = if (comment.isMine) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            fontWeight = FontWeight.Bold,
        )
        if (comment.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = "${comment.author}的头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun CommentComposer(
    draft: String,
    submitting: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = draft.isNotBlank() && !submitting
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                start = SocialDimens.spaceMd,
                top = SocialDimens.spaceSm,
                end = SocialDimens.spaceSm,
                bottom = SocialDimens.spaceSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier
                .weight(1f)
                .testTag("comment_input"),
            placeholder = { Text(if (submitting) "正在发送…" else "说点什么…") },
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (canSend) onSend()
                },
            ),
            shape = AuroraShapes.Capsule,
        )
        Spacer(Modifier.width(SocialDimens.spaceXs))
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(SocialDimens.touchTarget)
                .then(
                    if (canSend) {
                        Modifier
                            .clip(CircleShape)
                            .background(AuroraGradient, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .testTag("comment_send_button"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送评论",
                tint = if (canSend) {
                    ArcticColors.Background
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                },
            )
        }
    }
}
