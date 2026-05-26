package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.flower_show.model.ImageCardItem

@Composable
fun ImageCard(
    card: ImageCardItem,
    modifier: Modifier = Modifier,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isCollected by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl).crossfade(true).build(),
            contentDescription = "图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Bottom gradient
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        // Right-side interaction
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLiked) HeartFilledIcon(size = 32.dp, onClick = { isLiked = !isLiked })
            else HeartOutlineIcon(size = 32.dp, onClick = { isLiked = !isLiked })
            Text(formatCount(if (isLiked) card.likes + 1 else card.likes), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(22.dp))
            CommentIcon(size = 32.dp)
            Text(formatCount(card.comments), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(22.dp))
            BookmarkIcon(tint = if (isCollected) Color(0xFFFFD700) else Color.White, size = 32.dp, onClick = { isCollected = !isCollected })
            Text("收藏", color = Color.White, fontSize = 12.sp)

            Spacer(Modifier.height(22.dp))
            ShareIcon(size = 32.dp)
            Text("分享", color = Color.White, fontSize = 12.sp)
        }

        // Bottom-left info
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 80.dp, bottom = 24.dp),
        ) {
            Text("@${card.author}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(card.title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
