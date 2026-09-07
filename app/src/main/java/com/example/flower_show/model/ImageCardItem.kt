package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 单张图片卡片的数据模型。
 *
 * 它和 VideoItem 一样都会出现在首页信息流里，但它没有播放器字段，
 * UI 层会根据 itemType 把它交给 ImageCard 渲染。
 */
@Immutable
data class ImageCardItem(
    /**
     * 图片内容的唯一 id。
     *
     * 用途和 VideoItem.id 类似：列表定位、搜索跳转、日志统计都需要稳定 id。
     */
    val id: String,

    /**
     * 图片标题或描述。
     *
     * 搜索时会参与关键词匹配，UI 中也会作为主要文案展示。
     */
    val title: String,

    /**
     * 作者昵称。
     */
    val author: String,

    /**
     * 图片地址。
     *
     * ImageCard 会通过图片加载库读取这个地址并显示图片。
     */
    val imageUrl: String,

    /**
     * 点赞数，用于 UI 右侧/底部互动数据展示，也可用于搜索热度排序。
     */
    val likes: Int = 0,

    /**
     * 评论数。
     */
    val comments: Int = 0,

    /**
     * 背景音乐地址。
     *
     * 单图内容也可能有配乐；当前阶段你只需要知道这是“预留给播放音乐/展示音乐信息”的字段。
     */
    val bgMusicUrl: String = "",

    /** Viewer-specific interaction state; absent legacy fields map to false. */
    val likedByViewer: Boolean = false,
    val favoritedByViewer: Boolean = false,
) : CardItem {
    /**
     * 声明自己是一张图片卡片。
     */
    override val itemType: CardItem = CardItem.TypeImage
}
