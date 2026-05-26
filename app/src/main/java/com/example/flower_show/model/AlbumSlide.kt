package com.example.flower_show.model

/**
 * AlbumSlide - Single slide in an album / 图集中一张幻灯片
 */
data class AlbumSlide(
    val type: Int,          // TYPE_IMAGE=0 or TYPE_VIDEO=1
    val mediaUrl: String,   // Image or video URL
) {
    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }
    val isVideo: Boolean get() = type == TYPE_VIDEO
    val isImage: Boolean get() = type == TYPE_IMAGE
}
