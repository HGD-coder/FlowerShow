package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 图集卡片的数据模型。
 *
 * 图集可以理解成“一条内容里包含多张幻灯片”，每一页由 AlbumSlide 表示。
 * UI 层通常会用 HorizontalPager 横向滑动展示 slides。
 */
@Immutable
data class AlbumCardItem(
    /**
     * 图集内容的唯一 id。
     */
    val id: String,

    /**
     * 图集标题或描述。
     */
    val title: String,

    /**
     * 作者昵称。
     */
    val author: String,

    /**
     * 作者头像地址。
     */
    val avatarUrl: String,

    /**
     * 图集里的所有页。
     *
     * 每个 AlbumSlide 可以是图片，也可以是视频。
     * 读 UI 时看到 pagerState.currentPage，就可以对应到这里的 slides[index]。
     */
    val slides: List<AlbumSlide>,

    /**
     * 图集背景音乐地址。
     */
    val bgMusicUrl: String = "",

    /**
     * 点赞数。
     */
    val likes: Int = 0,

    /**
     * 评论数。
     */
    val comments: Int = 0,

    /**
     * 分享数。
     */
    val shares: Int = 0,

    /**
     * 图集标签，用于搜索、推荐或 UI 展示。
     */
    val tags: List<String> = emptyList(),

    /**
     * 推荐搜索词。
     */
    val recommendWords: List<String> = emptyList(),

    /** Viewer-specific interaction state; absent legacy fields map to false. */
    val likedByViewer: Boolean = false,
    val favoritedByViewer: Boolean = false,
) : CardItem {
    /**
     * 声明自己是一张图集卡片。
     */
    override val itemType: CardItem = CardItem.TypeAlbum

    /**
     * 图集页数。
     *
     * 这里写成计算属性，而不是额外存一个 Int，
     * 是为了避免 slideCount 和 slides.size 不一致。
     */
    val slideCount: Int get() = slides.size
}
