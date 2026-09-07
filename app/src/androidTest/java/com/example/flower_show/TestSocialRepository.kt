package com.example.flower_show

import com.example.flower_show.data.repository.FollowResult
import com.example.flower_show.data.repository.ISocialRepository
import com.example.flower_show.data.repository.ProfileVideoPage
import com.example.flower_show.data.repository.SocialProfilePage
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.Result
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile

internal class TestSocialRepository : ISocialRepository {
    private val people = listOf(
        profile("friend-linwu", "linwu_daily", "林雾", following = true, followedBy = true),
        profile("user-ayao", "ayao_flower", "阿遥", following = false, followedBy = true),
    )

    override fun loadProfile(userId: String): Result<SocialProfile> =
        Result.success(
            people.firstOrNull { it.id == userId }
                ?: profile(userId, "instrumented.user", "测试用户"),
        )

    override fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ): Result<ProfileVideoPage> = Result.success(
        ProfileVideoPage(
            items = listOf(
                ProfileVideo(
                    id = "$tab-video",
                    title = "测试视频",
                    author = "测试用户",
                    coverUrl = "",
                    authorUserId = userId,
                ),
            ),
            hasMore = false,
        ),
    )

    override fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ): Result<SocialProfilePage> {
        val filtered = people.filter {
            keyword.isBlank() ||
                it.nickname.contains(keyword, ignoreCase = true) ||
                it.account.contains(keyword, ignoreCase = true)
        }
        return Result.success(SocialProfilePage(filtered, hasMore = false))
    }

    override fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ): Result<FollowResult> = Result.success(
        FollowResult(
            targetUserId = targetUserId,
            following = following,
            followedBy = true,
            mutualFollow = following,
            followerCount = 10,
            actorFollowingCount = if (following) 2 else 1,
        ),
    )

    override fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ): Result<Boolean> = Result.success(visible)

    override fun loadNotifications(
        cursor: String?,
        pageSize: Int,
    ): Result<CursorPage<SocialNotification>> =
        Result.success(CursorPage(emptyList(), null, hasMore = false))

    override fun loadNotificationUnreadCount(): Result<Int> = Result.success(0)

    override fun markNotificationRead(notificationId: String): Result<Int> = Result.success(0)

    override fun markAllNotificationsRead(): Result<Int> = Result.success(0)

    private fun profile(
        id: String,
        account: String,
        nickname: String,
        following: Boolean = false,
        followedBy: Boolean = false,
    ) = SocialProfile(
        id = id,
        account = account,
        nickname = nickname,
        bio = "测试简介",
        region = "上海",
        avatarUrl = "",
        worksCount = 1,
        followingCount = 1,
        followersCount = 1,
        likesReceivedCount = 1,
        isFollowing = following,
        followsMe = followedBy,
    )
}
