package com.example.flower_show.model

import androidx.compose.runtime.Immutable

@Immutable
data class ProfileVideo(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val isPublic: Boolean = true,
    val isVisibleOnProfile: Boolean = true,
    val authorUserId: String? = null,
)

@Immutable
data class SocialProfile(
    val id: String,
    val account: String,
    val nickname: String,
    val bio: String,
    val region: String,
    val avatarUrl: String,
    val worksCount: Int,
    val followingCount: Int,
    val followersCount: Int,
    val likesReceivedCount: Int,
    val isFollowing: Boolean = false,
    val followsMe: Boolean = false,
    val works: List<ProfileVideo> = emptyList(),
    val likedVideos: List<ProfileVideo> = emptyList(),
    val collectedVideos: List<ProfileVideo> = emptyList(),
) {
    val isMutual: Boolean get() = isFollowing && followsMe

    companion object {
        val Empty = SocialProfile(
            id = "",
            account = "",
            nickname = "",
            bio = "",
            region = "",
            avatarUrl = "",
            worksCount = 0,
            followingCount = 0,
            followersCount = 0,
            likesReceivedCount = 0,
        )
    }
}

@Immutable
data class SocialNotification(
    val id: String,
    val actorUserId: String? = null,
    val actorNickname: String? = null,
    val actorAvatarUrl: String? = null,
    val type: String,
    val contentId: String? = null,
    val commentId: String? = null,
    val message: String,
    val isRead: Boolean,
    val createdAt: String,
)

@Immutable
data class ContentComment(
    val id: String,
    val contentId: String,
    val userId: String,
    val nickname: String,
    val avatarUrl: String,
    val parentId: String? = null,
    val body: String,
    val likeCount: Int,
    val createdAt: String,
)

@Immutable
data class ContentStats(
    val contentId: String,
    val likeCount: Int,
    val commentCount: Int,
    val favoriteCount: Int,
    val shareCount: Int,
    val viewCount: Int,
)

@Immutable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
