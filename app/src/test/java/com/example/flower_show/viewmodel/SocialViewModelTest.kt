package com.example.flower_show.viewmodel

import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.repository.FollowResult
import com.example.flower_show.data.repository.ISocialRepository
import com.example.flower_show.data.repository.ProfileVideoPage
import com.example.flower_show.data.repository.SocialProfilePage
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.Result
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialViewModelTest {
    private val user = AuthUser(
        userId = "server-user-42",
        accountId = "account-9",
        username = "garden.user",
        nickname = "花园用户",
        avatarUrl = "https://example.com/avatar.jpg",
        role = "USER",
    )

    @Test
    fun authenticatedIdentityLoadsBackendProfile() {
        val repository = FakeSocialRepository()
        val viewModel = SocialViewModel(repository, Dispatchers.Unconfined)

        viewModel.dispatch(SocialIntent.BindAuthenticatedUser(user))

        val state = viewModel.state.value
        assertEquals("server-user-42", state.myProfile.id)
        assertEquals(repository.profile.nickname, state.myProfile.nickname)
        assertEquals(repository.works, state.myProfile.works)
    }

    @Test
    fun allProfileTabsLoadTheirBackendCollections() {
        val repository = FakeSocialRepository()
        val viewModel = SocialViewModel(repository, Dispatchers.Unconfined)
        viewModel.dispatch(SocialIntent.BindAuthenticatedUser(user))

        viewModel.dispatch(SocialIntent.SelectProfileTab(ProfileContentTab.Likes))
        viewModel.dispatch(SocialIntent.SelectProfileTab(ProfileContentTab.Collections))

        assertEquals(listOf("posts", "liked", "favorites"), repository.loadedTabs)
        assertEquals(repository.liked, viewModel.state.value.myProfile.likedVideos)
        assertEquals(repository.favorites, viewModel.state.value.myProfile.collectedVideos)
    }

    @Test
    fun relationsComeFromRepositoryAndFollowUsesAuthoritativeCounts() {
        val repository = FakeSocialRepository()
        val viewModel = SocialViewModel(repository, Dispatchers.Unconfined)
        viewModel.dispatch(SocialIntent.BindAuthenticatedUser(user))
        viewModel.dispatch(SocialIntent.SelectRelationTab(RelationListTab.Followers))

        val target = viewModel.state.value.relationProfiles.single()
        assertFalse(target.isFollowing)

        viewModel.dispatch(SocialIntent.ToggleFollow(target.id))

        val updated = viewModel.state.value.relationProfiles.single()
        assertTrue(updated.isFollowing)
        assertTrue(updated.isMutual)
        assertEquals(8, updated.followersCount)
        assertEquals(4, viewModel.state.value.myProfile.followingCount)
        assertEquals(listOf(false), repository.followStatesBeforeWrite)
    }

    @Test
    fun workVisibilityCallsBackendAndKeepsReturnedValue() {
        val repository = FakeSocialRepository()
        val viewModel = SocialViewModel(repository, Dispatchers.Unconfined)
        viewModel.dispatch(SocialIntent.BindAuthenticatedUser(user))

        viewModel.dispatch(SocialIntent.SetWorkVisibility(repository.works.single().id, false))

        assertEquals(listOf("work-1" to false), repository.visibilityWrites)
        assertFalse(viewModel.state.value.myProfile.works.single().isVisibleOnProfile)
    }

    @Test
    fun notificationsLoadAndReadActionsUseRepositoryCounts() {
        val repository = FakeSocialRepository()
        val viewModel = SocialViewModel(repository, Dispatchers.Unconfined)

        viewModel.dispatch(SocialIntent.LoadNotifications)
        assertEquals(2, viewModel.state.value.notificationUnreadCount)
        assertFalse(viewModel.state.value.notifications.single().isRead)

        viewModel.dispatch(SocialIntent.MarkNotificationRead("notification-1"))
        assertTrue(viewModel.state.value.notifications.single().isRead)
        assertEquals(1, viewModel.state.value.notificationUnreadCount)

        viewModel.dispatch(SocialIntent.MarkAllNotificationsRead)
        assertEquals(0, viewModel.state.value.notificationUnreadCount)
        assertEquals(listOf("notification-1"), repository.readNotifications)
        assertEquals(1, repository.markAllCalls)
    }

}

private class FakeSocialRepository : ISocialRepository {
    val works = listOf(ProfileVideo("work-1", "作品", "花园用户", "cover"))
    val liked = listOf(ProfileVideo("liked-1", "点赞", "作者", "cover"))
    val favorites = listOf(ProfileVideo("favorite-1", "收藏", "作者", "cover"))
    val profile = SocialProfile(
        id = "server-user-42",
        account = "garden.user",
        nickname = "服务端昵称",
        bio = "服务端简介",
        region = "上海",
        avatarUrl = "",
        worksCount = 1,
        followingCount = 3,
        followersCount = 5,
        likesReceivedCount = 9,
    )
    val relation = SocialProfile(
        id = "backend-user",
        account = "backend",
        nickname = "后端用户",
        bio = "",
        region = "",
        avatarUrl = "",
        worksCount = 1,
        followingCount = 2,
        followersCount = 7,
        likesReceivedCount = 3,
        isFollowing = false,
        followsMe = true,
    )
    val loadedTabs = mutableListOf<String>()
    val followStatesBeforeWrite = mutableListOf<Boolean>()
    val visibilityWrites = mutableListOf<Pair<String, Boolean>>()
    val readNotifications = mutableListOf<String>()
    var markAllCalls = 0

    override fun loadProfile(userId: String): Result<SocialProfile> = Result.success(profile)

    override fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ): Result<ProfileVideoPage> {
        loadedTabs += tab
        val items = when (tab) {
            "posts" -> works
            "liked" -> liked
            "favorites" -> favorites
            else -> emptyList()
        }
        return Result.success(ProfileVideoPage(items, hasMore = false))
    }

    override fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ): Result<SocialProfilePage> =
        Result.success(SocialProfilePage(listOf(this.relation), hasMore = false))

    override fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ): Result<FollowResult> {
        followStatesBeforeWrite += !following
        return Result.success(
            FollowResult(
                targetUserId = targetUserId,
                following = following,
                followedBy = true,
                mutualFollow = following,
                followerCount = 8,
                actorFollowingCount = 4,
            ),
        )
    }

    override fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ): Result<Boolean> {
        visibilityWrites += contentId to visible
        return Result.success(visible)
    }

    override fun loadNotifications(
        cursor: String?,
        pageSize: Int,
    ): Result<CursorPage<SocialNotification>> = Result.success(
        CursorPage(
            items = listOf(
                SocialNotification(
                    id = "notification-1",
                    actorUserId = "backend-user",
                    actorNickname = "后端用户",
                    type = "follow",
                    message = "关注了你",
                    isRead = false,
                    createdAt = "2026-07-23T00:00:00Z",
                ),
            ),
            nextCursor = null,
            hasMore = false,
        ),
    )

    override fun loadNotificationUnreadCount(): Result<Int> = Result.success(2)

    override fun markNotificationRead(notificationId: String): Result<Int> {
        readNotifications += notificationId
        return Result.success(1)
    }

    override fun markAllNotificationsRead(): Result<Int> {
        markAllCalls++
        return Result.success(0)
    }
}
