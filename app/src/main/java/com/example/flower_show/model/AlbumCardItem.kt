package com.example.flower_show.model

data class AlbumCardItem(
    val id: String,
    val title: String,
    val author: String,
    val avatarUrl: String,
    val slides: List<AlbumSlide>,
    val bgMusicUrl: String = "",
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val tags: List<String> = emptyList(),
    val recommendWords: List<String> = emptyList(),
) : CardItem {
    override val itemType: CardItem = CardItem.TypeAlbum
    val slideCount: Int get() = slides.size
}
