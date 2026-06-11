package com.example.flower_show.model

import org.junit.Assert.*
import org.junit.Test

class CardItemTest {

    // ── Positive tests ──

    @Test
    fun videoItem_isCardItem() {
        val video = VideoItem("v001", "Test", "Author", "a", "v")
        assertTrue(video is CardItem)
    }

    @Test
    fun imageCardItem_isCardItem() {
        val img = ImageCardItem("i001", "Test", "Author", "url", 100, 20)
        assertTrue(img is CardItem)
    }

    @Test
    fun albumCardItem_isCardItem() {
        val album = AlbumCardItem("a001", "Test", "Author", "a", emptyList(), likes = 10)
        assertTrue(album is CardItem)
    }

    @Test
    fun typeVideo_instance_isSingleton() {
        assertSame(CardItem.TypeVideo, CardItem.TypeVideo)
    }

    @Test
    fun typeImage_instance_isSingleton() {
        assertSame(CardItem.TypeImage, CardItem.TypeImage)
    }

    @Test
    fun videoItemType_isTypeVideo() {
        val video = VideoItem("v001", "A", "A", "a", "v")
        assertEquals(CardItem.TypeVideo, video.itemType)
    }

    @Test
    fun imageItemType_isTypeImage() {
        val img = ImageCardItem("i001", "A", "A", "url")
        assertEquals(CardItem.TypeImage, img.itemType)
    }

    @Test
    fun albumItemType_isTypeAlbum() {
        val album = AlbumCardItem("a001", "A", "A", "a", emptyList())
        assertEquals(CardItem.TypeAlbum, album.itemType)
    }

    // ── Boundary tests ──

    @Test
    fun videoItem_emptyLists_isValid() {
        val video = VideoItem("v001", "A", "A", "a", "v",
            tags = emptyList(), recommendWords = emptyList())
        assertTrue(video.tags.isEmpty())
        assertTrue(video.recommendWords.isEmpty())
    }

    @Test
    fun imageCardItem_zeroCounts_isValid() {
        val img = ImageCardItem("i001", "A", "A", "url", 0, 0)
        assertEquals(0, img.likes)
        assertEquals(0, img.comments)
    }

    @Test
    fun albumSlidesCount_singleSlide_returns1() {
        val album = AlbumCardItem("a001", "A", "A", "a",
            slides = listOf(AlbumSlide(0, "url")))
        assertEquals(1, album.slideCount)
    }

    @Test
    fun albumSlidesCount_multipleSlides_correctCount() {
        val album = AlbumCardItem("a001", "A", "A", "a",
            slides = (1..5).map { AlbumSlide(0, "url$it") })
        assertEquals(5, album.slideCount)
    }

    // ── Reverse/negative tests ──

    @Test
    fun videoItemCopy_changeTitle_preservesOtherFields() {
        val original = VideoItem("v001", "Title1", "Author", "a", "v",
            likes = 99, comments = 10, collections = 5, shares = 3,
            tags = listOf("t1"), recommendWords = listOf("r1"))
        val copy = original.copy(title = "Title2")
        assertEquals("Title2", copy.title)
        assertEquals(99, copy.likes)
        assertEquals(listOf("t1"), copy.tags)
    }

    @Test
    fun albumSlide_isVideo_returnsFalseForImageType() {
        val slide = AlbumSlide(AlbumSlide.TYPE_IMAGE, "url")
        assertTrue(slide.isImage)
        assertFalse(slide.isVideo)
    }

    @Test
    fun albumSlide_isVideo_returnsTrueForVideoType() {
        val slide = AlbumSlide(AlbumSlide.TYPE_VIDEO, "url")
        assertTrue(slide.isVideo)
        assertFalse(slide.isImage)
    }

    @Test
    fun cardItemDifferentTypes_notEqual() {
        val video = VideoItem("x", "A", "A", "a", "v")
        val img = ImageCardItem("x", "A", "A", "url")
        assertNotEquals(video.itemType, img.itemType)
    }

    @Test
    fun cardItemTypeObjects_areNotEqual() {
        assertNotEquals(CardItem.TypeVideo, CardItem.TypeImage)
        assertNotEquals(CardItem.TypeImage, CardItem.TypeAlbum)
    }
}
