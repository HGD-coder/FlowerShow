package com.example.flower_show.model

data class ImageCardItem(
    val id: String,
    val title: String,
    val author: String,
    val imageUrl: String,
    val likes: Int = 0,
    val comments: Int = 0,
) : CardItem {
    override val itemType: CardItem = CardItem.TypeImage
}
