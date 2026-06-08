package com.example.flower_show.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.flower_show.model.VideoItem

enum class FlowerImageSlot(
    val widthPx: Int,
    val heightPx: Int,
    val cachePrefix: String,
    val crossfade: Boolean,
) {
    VideoCover(widthPx = 720, heightPx = 1_280, cachePrefix = "video_cover", crossfade = false),
    FeedImage(widthPx = 1_080, heightPx = 1_920, cachePrefix = "feed_image", crossfade = false),
    SearchThumbnail(widthPx = 320, heightPx = 192, cachePrefix = "search_thumb", crossfade = false),
    Avatar(widthPx = 128, heightPx = 128, cachePrefix = "avatar", crossfade = false),
    DiscAvatar(widthPx = 96, heightPx = 96, cachePrefix = "disc_avatar", crossfade = false),
}

@Composable
fun rememberFlowerImageRequest(
    data: String,
    slot: FlowerImageSlot,
): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, slot) {
        FlowerImageRequests.create(context, data, slot)
    }
}

object FlowerImageRequests {
    fun create(
        context: Context,
        data: String,
        slot: FlowerImageSlot,
    ): ImageRequest {
        val cacheData = data.takeIf { it.isNotBlank() }
        val memoryCacheKey = cacheData?.let { "${slot.cachePrefix}:$it" }
        return ImageRequest.Builder(context)
            .data(cacheData)
            .size(slot.widthPx, slot.heightPx)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCacheKey(memoryCacheKey)
            .diskCacheKey(cacheData)
            .crossfade(slot.crossfade)
            .build()
    }
}

fun VideoItem.preferredCoverUrl(): String {
    return coverThumbnailUrl.ifBlank { coverUrl }
}
