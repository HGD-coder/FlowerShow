package com.example.flower_show.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun MaterialActionIcon(
    imageVector: ImageVector,
    tint: Color,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = clickableModifier.size(size),
    )
}

@Composable
fun HeartOutlineIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.FavoriteBorder,
        tint = tint,
        size = size,
        contentDescription = "点赞",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun HeartFilledIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFFF2D55),
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.Favorite,
        tint = tint,
        size = size,
        contentDescription = "已点赞",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun CommentIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.ChatBubbleOutline,
        tint = tint,
        size = size,
        contentDescription = "评论",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun BookmarkIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = if (filled) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        tint = tint,
        size = size,
        contentDescription = if (filled) "已收藏" else "收藏",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun ShareIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.Share,
        tint = tint,
        size = size,
        contentDescription = "分享",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun PlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.PlayArrow,
        tint = tint,
        size = size,
        contentDescription = "播放",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.Pause,
        tint = tint,
        size = size,
        contentDescription = "暂停",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun SearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.Search,
        tint = tint,
        size = size,
        contentDescription = "搜索",
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
) {
    MaterialActionIcon(
        imageVector = Icons.Filled.Close,
        tint = tint,
        size = size,
        contentDescription = "关闭",
        modifier = modifier,
        onClick = onClick,
    )
}
