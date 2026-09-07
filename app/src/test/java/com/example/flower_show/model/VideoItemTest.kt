package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test
import com.example.flower_show.ui.component.preferredCoverUrl

class VideoItemTest {

    @Test
    fun allFieldsSetCorrectly() {
        val video = VideoItem(
            id = "v001", title = "Test", author = "Author",
            avatarUrl = "https://a.jpg", videoUrl = "https://v.mp4",
            likes = 100, comments = 20, collections = 5, shares = 10,
            tags = listOf("tag1"), recommendWords = listOf("rec1"),
            authorUserId = "user-1",
            likedByViewer = true,
            favoritedByViewer = true,
        )
        assertEquals("v001", video.id)
        assertEquals(100, video.likes)
        assertEquals(listOf("tag1"), video.tags)
        assertEquals("user-1", video.authorUserId)
        assertTrue(video.likedByViewer)
        assertTrue(video.favoritedByViewer)
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

    @Test
    fun preferredCoverUrlUsesThumbnailWhenAvailable() {
        val video = VideoItem(
            id = "v001",
            title = "A",
            author = "A",
            avatarUrl = "a",
            videoUrl = "v",
            coverUrl = "cover.jpg",
            coverThumbnailUrl = "cover_360.jpg",
        )

        assertEquals("cover_360.jpg", video.preferredCoverUrl())
    }

    @Test
    fun preferredCoverUrlFallsBackToCover() {
        val video = VideoItem(
            id = "v001",
            title = "A",
            author = "A",
            avatarUrl = "a",
            videoUrl = "v",
            coverUrl = "cover.jpg",
        )

        assertEquals("cover.jpg", video.preferredCoverUrl())
    }

    @Test
    fun hlsUrlKeepsProgressiveFallback() {
        val video = VideoItem(
            id = "v001",
            title = "A",
            author = "A",
            avatarUrl = "a",
            videoUrl = "https://cdn.example/video.mp4",
            qualityUrls = mapOf("360p" to "https://cdn.example/video_360p.mp4"),
            hlsUrl = "https://cdn.example/hls/master.m3u8",
        )

        assertEquals("https://cdn.example/hls/master.m3u8", video.hlsUrl)
        assertEquals("https://cdn.example/video.mp4", video.videoUrl)
    }
}
