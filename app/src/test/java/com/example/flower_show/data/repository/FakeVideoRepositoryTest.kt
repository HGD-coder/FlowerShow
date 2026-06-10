package com.example.flower_show.data.repository

import org.junit.Assert.*
import org.junit.Test
import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.model.Result
import com.example.flower_show.model.VideoItem

class FakeVideoRepositoryTest {

    @Test
    fun loadFeed_page1_returnsCorrectSize() {
        val repo = repo()
        val result = repo.loadFeed(1, 10)
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(10, data.size)
    }

    @Test
    fun loadFeed_page2_returnsRemaining() {
        val repo = repo()
        val result = repo.loadFeed(2, 10)
        assertTrue(result.isSuccess)
    }

    @Test
    fun loadFeed_repeatedPageUsesStableSessionOrder() {
        val repo = repo()
        val first = (repo.loadFeed(1, 10) as Result.Success).data
        val second = (repo.loadFeed(1, 10) as Result.Success).data

        assertEquals(first.map { it.feedTestKey() }, second.map { it.feedTestKey() })
    }

    @Test
    fun loadFeed_pagesDoNotOverlapWithinSession() {
        val repo = repo()
        val first = (repo.loadFeed(1, 5) as Result.Success).data.map { it.feedTestKey() }.toSet()
        val second = (repo.loadFeed(2, 5) as Result.Success).data.map { it.feedTestKey() }.toSet()

        assertTrue(first.intersect(second).isEmpty())
    }

    @Test
    fun refreshFeedSession_keepsNewSessionStable() {
        val repo = repo()
        repo.refreshFeedSession()
        val first = (repo.loadFeed(1, 10) as Result.Success).data
        val second = (repo.loadFeed(1, 10) as Result.Success).data

        assertEquals(first.map { it.feedTestKey() }, second.map { it.feedTestKey() })
    }

    @Test
    fun loadFeed_beyondRange_returnsEmpty() {
        val repo = repo()
        val result = repo.loadFeed(100, 10)
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun search_exactTitleMatch() {
        val repo = repo()
        val result = repo.search("梅西")
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isNotEmpty())
    }

    @Test
    fun search_noMatch_returnsEmpty() {
        val repo = repo()
        val result = repo.search("不存在的内容XYZ123")
        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun search_shrimpKeyword_returnsSuccess() {
        val repo = repo()
        val result = repo.search("虾")

        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isNotEmpty())
    }

    @Test
    fun search_compoundCrispyShrimpKeyword_returnsRelatedResults() {
        val repo = repo()
        val result = repo.search("脆皮虾")

        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isNotEmpty())
    }

    @Test
    fun search_duplicateVideoIds_returnsUniqueItems() {
        val duplicated = VideoItem(
            id = "same-id",
            title = "脆皮虾滑做法",
            author = "测试作者",
            avatarUrl = "",
            videoUrl = "https://example.com/same.mp4",
            tags = listOf("虾滑的做法"),
        )
        val repo = FakeVideoRepository.withVideos(
            listOf(
                duplicated,
                duplicated.copy(title = "脆皮虾滑做法 第二条重复数据"),
            ),
        )

        val result = repo.search("脆皮虾")

        assertTrue(result.isSuccess)
        assertEquals(1, (result as Result.Success).data.size)
    }

    @Test
    fun repositoryWithoutAssets_returnsEmptyFeed() {
        val result = FakeVideoRepository.withVideos(emptyList()).loadFeed(1, 10)

        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    private fun repo(): FakeVideoRepository {
        return FakeVideoRepository.withVideos(testVideos())
    }

    private fun testVideos(): List<VideoItem> {
        return (1..12).map { index ->
            VideoItem(
                id = "v${index.toString().padStart(3, '0')}",
                title = when (index) {
                    1 -> "梅西生涯十佳进球集锦"
                    2 -> "神仙吃法 土豆虾滑卷 焦香的土豆裹着Q弹的虾滑"
                    else -> "测试视频 $index"
                },
                author = "测试作者",
                avatarUrl = "",
                videoUrl = "https://example.com/video$index.mp4",
                tags = when (index) {
                    1 -> listOf("足球", "梅西")
                    2 -> listOf("美食教程", "虾滑的做法")
                    else -> listOf("测试")
                },
                recommendWords = when (index) {
                    1 -> listOf("梅西世界杯")
                    2 -> listOf("土豆虾滑卷")
                    else -> emptyList()
                },
            )
        }
    }

    private fun CardItem.feedTestKey(): String = when (this) {
        is VideoItem -> "video:$id"
        is ImageCardItem -> "image:$id"
        is AlbumCardItem -> "album:$id"
        CardItem.TypeVideo -> "type_video"
        CardItem.TypeImage -> "type_image"
        CardItem.TypeAlbum -> "type_album"
    }
}
