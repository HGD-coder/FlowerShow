package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.ui.theme.ArcticColors

@Composable
fun AlbumCard(
    card: AlbumCardItem,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { card.slideCount })
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }
    var isCollected by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(card.slides[page].mediaUrl).crossfade(true).build(),
                contentDescription = "slide ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

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

        SlideProgressBar(
            slideCount = card.slideCount,
            currentSlide = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 224.dp),
        )

        TikTokCaptionPanel(
            author = card.author,
            title = card.title,
            subtitle = card.recommendWords.firstOrNull() ?: "This is a TikTok subtitle.",
            onSubtitleClick = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 98.dp, bottom = 112.dp),
        )

        TikTokActionRail(
            avatarUrl = card.avatarUrl,
            isLiked = isLiked,
            isCollected = isCollected,
            likes = card.likes,
            comments = card.comments,
            collections = 0,
            shares = card.shares,
            onLikeClick = { isLiked = !isLiked },
            onCollectClick = { isCollected = !isCollected },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 112.dp),
        )
    }
}
