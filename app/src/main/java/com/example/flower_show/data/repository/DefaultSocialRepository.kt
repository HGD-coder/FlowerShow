package com.example.flower_show.data.repository

import com.example.flower_show.data.remote.SocialApi
import com.example.flower_show.data.remote.toModel
import com.example.flower_show.data.remote.toProfileVideo
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.Result

class DefaultSocialRepository(
    private val api: SocialApi,
) : ISocialRepository {

    override fun loadProfile(userId: String) = result {
        api.profile(userId).toModel()
    }

    override fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ) = result {
        val response = api.profileContents(userId, tab, page, pageSize)
        ProfileVideoPage(
            items = response.items.orEmpty().mapNotNull { it.toProfileVideo(userId) },
            hasMore = response.hasMore ?: false,
        )
    }

    override fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ) = result {
        val response = api.relations(userId, relation, keyword, page, pageSize)
        SocialProfilePage(
            items = response.items.orEmpty()
                .map { it.toModel() }
                .filter { it.id.isNotBlank() },
            hasMore = response.hasMore ?: false,
        )
    }

    override fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ) = result {
        val response = if (following) {
            api.follow(actorUserId, targetUserId)
        } else {
            api.unfollow(actorUserId, targetUserId)
        }
        FollowResult(
            targetUserId = response.targetUserId.orEmpty().ifBlank { targetUserId },
            following = response.following ?: following,
            followedBy = response.followedBy ?: false,
            mutualFollow = response.mutualFollow
                ?: ((response.following ?: following) && (response.followedBy ?: false)),
            followerCount = response.followerCount.asCount(),
            actorFollowingCount = response.followingCount.asCount(),
        )
    }

    override fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ) = result {
        api.updateProfileDisplay(userId, contentId, visible).showOnProfile ?: visible
    }

    override fun loadNotifications(cursor: String?, pageSize: Int) = result {
        val response = api.notifications(cursor, pageSize)
        CursorPage(
            items = response.items.orEmpty()
                .map { it.toModel() }
                .filter { it.id.isNotBlank() },
            nextCursor = response.nextCursor,
            hasMore = (response.hasMore ?: false) && !response.nextCursor.isNullOrBlank(),
        )
    }

    override fun loadNotificationUnreadCount() = result {
        api.notificationUnreadCount().unreadCount.asCount()
    }

    override fun markNotificationRead(notificationId: String) = result {
        api.markNotificationRead(notificationId).unreadCount.asCount()
    }

    override fun markAllNotificationsRead() = result {
        api.markAllNotificationsRead().unreadCount.asCount()
    }

    private fun <T> result(action: () -> T): Result<T> = try {
        Result.success(action())
    } catch (e: Exception) {
        Result.error(e.message ?: "Social request failed")
    }

    private fun Long?.asCount(): Int =
        (this ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
