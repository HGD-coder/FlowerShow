package com.example.flower_show.viewmodel

import com.example.flower_show.model.DeliveryContext
import com.example.flower_show.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFeedSessionPolicyTest {
    @Test
    fun normalNavigationReturnResumesCurrentFeedWithoutRefreshing() {
        assertEquals(
            HomeFeedEntryAction.ResumeCurrent,
            resolveHomeFeedEntry(
                hasItems = true,
                isLoading = false,
                isCatalogPositioning = false,
                hasPendingTarget = false,
            ),
        )
    }

    @Test
    fun emptyOrSearchCatalogEntryReloadsRecommendedFeed() {
        assertEquals(
            HomeFeedEntryAction.Reload,
            resolveHomeFeedEntry(
                hasItems = false,
                isLoading = false,
                isCatalogPositioning = false,
                hasPendingTarget = false,
            ),
        )
        assertEquals(
            HomeFeedEntryAction.Reload,
            resolveHomeFeedEntry(
                hasItems = true,
                isLoading = false,
                isCatalogPositioning = true,
                hasPendingTarget = false,
            ),
        )
    }

    @Test
    fun viewerBindingRefreshesOnlyWhenIdentityActuallyChanges() {
        val binding = ViewerFeedBinding()

        assertFalse(binding.bind(null))
        assertFalse(binding.bind(null))
        assertTrue(binding.bind("user-a"))
        assertFalse(binding.bind(" user-a "))
        assertTrue(binding.bind("user-b"))
        assertTrue(binding.bind(null))
    }

    @Test
    fun restoredAuthenticatedViewerRefreshesGuestStartupFeedOnce() {
        val binding = ViewerFeedBinding()

        assertTrue(binding.bind("user-a"))
        assertFalse(binding.bind("user-a"))
    }

    @Test
    fun paginationAppendsOnlyUniqueItemsAndKeepsDeliveryAlignment() {
        val existing = video("v1")
        val duplicate = video("v1")
        val next = video("v2")

        val merged = mergeFeedItems(
            existingItems = listOf(existing),
            existingDeliveryContexts = listOf(delivery("d1", "v1")),
            incomingItems = listOf(duplicate, next),
            incomingDeliveryContexts = listOf(
                delivery("duplicate-delivery", "v1"),
                delivery("d2", "v2"),
            ),
        )

        assertEquals(listOf("v1", "v2"), merged.items.map { (it as VideoItem).id })
        assertEquals(listOf("d1", "d2"), merged.deliveryContexts.map { it?.id })
        assertEquals(1, merged.appendedCount)
    }

    private fun video(id: String) = VideoItem(
        id = id,
        title = id,
        author = "author",
        avatarUrl = "",
        videoUrl = "https://example.com/$id.mp4",
    )

    private fun delivery(id: String, contentId: String) = DeliveryContext(
        id = id,
        contentId = contentId,
        rank = 1,
        source = "test",
        deliveryType = "organic",
        exposureToken = "token-$id",
    )
}
