package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test

class VideoItemTest {

    @Test
    fun allFieldsSetCorrectly() {
        val video = VideoItem(
            id = "v001", title = "Test", author = "Author",
            avatarUrl = "https://a.jpg", videoUrl = "https://v.mp4",
            likes = 100, comments = 20, collections = 5, shares = 10,
            tags = listOf("tag1"), recommendWords = listOf("rec1"),
        )
        assertEquals("v001", video.id)
        assertEquals(100, video.likes)
        assertEquals(listOf("tag1"), video.tags)
    }

    @Test
    fun equalsByIdAndFields() {
        val v1 = VideoItem("v001", "A", "A", "a", "v")
        val v2 = VideoItem("v001", "A", "A", "a", "v")
        assertEquals(v1, v2)
    }

    @Test
    fun copyWorks() {
        val original = VideoItem("v001", "A", "A", "a", "v")
        val updated = original.copy(title = "B")
        assertEquals("B", updated.title)
        assertEquals("v001", updated.id)
    }

    @Test
    fun itemType_isTypeVideo() {
        val video = VideoItem("v001", "A", "A", "a", "v")
        assertEquals(CardItem.TypeVideo, video.itemType)
    }
}
