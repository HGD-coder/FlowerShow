package com.example.flower_show.model

/**
 * CardItem - Base type for all card types in the feed / 视频流中所有卡片类型的基类型
 *
 * sealed interface ensures exhaustive when() in Compose UI.
 * sealed interface 确保 Compose UI 中 when() 的穷举性。
 */
sealed interface CardItem {
    data object TypeVideo : CardItem { override val itemType get() = this }
    data object TypeImage : CardItem { override val itemType get() = this }
    data object TypeAlbum : CardItem { override val itemType get() = this }
    val itemType: CardItem
}
