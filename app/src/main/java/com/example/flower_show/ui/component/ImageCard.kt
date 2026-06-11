package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.ui.theme.ArcticColors

@Composable
fun ImageCard(
    card: ImageCardItem,
    modifier: Modifier = Modifier,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isCollected by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = rememberFlowerImageRequest(
                data = card.imageUrl,
                slot = FlowerImageSlot.FeedImage,
            ),
            contentDescription = "图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(ArcticColors.Background.copy(alpha = 0.48f), Color.Transparent),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ArcticColors.Background.copy(alpha = 0.84f)),
                    ),
                ),
        )

        VideoCaptionPanel(
            author = card.author,
            title = card.title,
            subtitle = null,
            onSubtitleClick = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 98.dp, bottom = 112.dp),
        )

        VideoActionRail(
            avatarUrl = card.imageUrl,
            isLiked = isLiked,
            isCollected = isCollected,
            likes = card.likes,
            comments = card.comments,
            collections = 0,
            shares = 0,
            onLikeClick = { isLiked = !isLiked },
            onCollectClick = { isCollected = !isCollected },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 112.dp),
        )
    }
}
