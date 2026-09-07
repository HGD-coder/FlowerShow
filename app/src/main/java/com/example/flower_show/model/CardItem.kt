package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 信息流里所有“卡片”的统一抽象。
 *
 * 这个项目的首页不是只有视频，还支持图片卡片、图集卡片。
 * UI 层拿到的是 List<CardItem>，再根据具体类型决定渲染 VideoCard、ImageCard 还是 AlbumCard。
 *
 * 你读代码时可以把它理解成：
 * - CardItem 是“所有卡片都必须遵守的共同身份”。
 * - VideoItem / ImageCardItem / AlbumCardItem 是真正带数据的卡片。
 * - TypeVideo / TypeImage / TypeAlbum 是给 UI 做快速类型判断用的标记。
 *
 * sealed interface 的好处：
 * 当 UI 使用 when(card) 或 when(card.itemType) 时，Kotlin 能知道目前一共有哪些类型，
 * 后续如果新增一种卡片，编译器会提醒哪些地方还没有处理。
 *
 * @Immutable 告诉 Compose：这个类型按不可变数据来使用。
 * 这样 UI 在重组时更容易判断数据是否真的变化，减少不必要的刷新。
 */
@Immutable
sealed interface CardItem {
    /**
     * 视频卡片的类型标记。
     * 注意：真正的视频数据不在这里，而是在 VideoItem 里。
     */
    data object TypeVideo : CardItem { override val itemType get() = this }

    /**
     * 单张图片卡片的类型标记。
     */
    data object TypeImage : CardItem { override val itemType get() = this }

    /**
     * 图集卡片的类型标记，一条内容里可以横向滑动多张图片/视频。
     */
    data object TypeAlbum : CardItem { override val itemType get() = this }

    /**
     * 每个卡片都暴露自己的类型。
     *
     * 为什么不只靠 `is VideoItem` 判断？
     * 因为有些地方只关心“卡片类别”，不需要读取完整字段；
     * itemType 可以让这类判断写得更直观。
     */
    val itemType: CardItem
}
