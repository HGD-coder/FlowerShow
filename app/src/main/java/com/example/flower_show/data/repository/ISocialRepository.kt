package com.example.flower_show.data.repository

import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.Result
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile

data class ProfileVideoPage(
    val items: List<ProfileVideo>,
    val hasMore: Boolean,
)

data class SocialProfilePage(
    val items: List<SocialProfile>,
    val hasMore: Boolean,
)

data class FollowResult(
    val targetUserId: String,
    val following: Boolean,
    val followedBy: Boolean,
    val mutualFollow: Boolean,
    val followerCount: Int,
    val actorFollowingCount: Int,
)

interface ISocialRepository {
    fun loadProfile(userId: String): Result<SocialProfile>

    fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<ProfileVideoPage>

    fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<SocialProfilePage>

    fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ): Result<FollowResult>

    fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ): Result<Boolean>

    fun loadNotifications(
        cursor: String? = null,
        pageSize: Int = 20,
    ): Result<CursorPage<SocialNotification>>

    fun loadNotificationUnreadCount(): Result<Int>

    fun markNotificationRead(notificationId: String): Result<Int>

    fun markAllNotificationsRead(): Result<Int>
}
