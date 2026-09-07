package com.example.flower_show.data.remote

import com.example.flower_show.model.AlbumCardItem
import com.example.flower_show.model.AlbumSlide
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.ContentComment
import com.example.flower_show.model.ContentStats
import com.example.flower_show.model.ImageCardItem
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.model.VideoItem

data class CardItemDto(
    val type: String? = null,
    val id: String? = null,
    val title: String? = null,
    val author: String? = null,
    val authorUserId: String? = null,
    val userId: String? = null,
    val avatarUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val coverThumbnailUrl: String? = null,
    val musicUrl: String? = null,
    val imageUrl: String? = null,
    val slides: List<AlbumSlideDto>? = null,
    val bgMusicUrl: String? = null,
    val likes: Int? = null,
    val comments: Int? = null,
    val collections: Int? = null,
    val shares: Int? = null,
    val tags: List<String>? = null,
    val recommendWords: List<String>? = null,
    val contentSearches: List<String>? = null,
    val qualityUrls: Map<String, String>? = null,
    val hlsUrl: String? = null,
    val likedByViewer: Boolean? = null,
    val favoritedByViewer: Boolean? = null,
)

data class AlbumSlideDto(
    val type: Int? = null,
    val mediaUrl: String? = null,
)

data class PageDto<T>(
    val items: List<T>? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val total: Long? = null,
    val hasMore: Boolean? = null,
)

data class CursorPageDto<T>(
    val items: List<T>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
)

data class UserProfileDto(
    val id: String? = null,
    val handle: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val following: Boolean? = null,
    val followedBy: Boolean? = null,
    val mutualFollow: Boolean? = null,
    val postCount: Long? = null,
    val receivedLikeCount: Long? = null,
    val followingCount: Long? = null,
    val followerCount: Long? = null,
)

data class UserListItemDto(
    val id: String? = null,
    val handle: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val followerCount: Long? = null,
    val followingCount: Long? = null,
    val following: Boolean? = null,
    val followedBy: Boolean? = null,
    val mutualFollow: Boolean? = null,
)

data class FollowActionResultDto(
    val changed: Boolean? = null,
    val targetUserId: String? = null,
    val following: Boolean? = null,
    val followedBy: Boolean? = null,
    val mutualFollow: Boolean? = null,
    val followerCount: Long? = null,
    val followingCount: Long? = null,
)

data class ProfileContentDisplayDto(
    val contentId: String? = null,
    val showOnProfile: Boolean? = null,
    val pinned: Boolean? = null,
    val sortOrder: Int? = null,
)

data class InteractionResultDto(
    val changed: Boolean? = null,
    val stats: ContentStatsDto? = null,
)

data class ContentStatsDto(
    val contentId: String? = null,
    val likeCount: Int? = null,
    val commentCount: Int? = null,
    val favoriteCount: Int? = null,
    val shareCount: Int? = null,
    val viewCount: Int? = null,
)

data class CommentDto(
    val id: String? = null,
    val contentId: String? = null,
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val parentId: String? = null,
    val body: String? = null,
    val likeCount: Int? = null,
    val createdAt: String? = null,
)

data class NotificationDto(
    val id: String? = null,
    val actor: NotificationActorDto? = null,
    val actorUserId: String? = null,
    val actorNickname: String? = null,
    val actorAvatarUrl: String? = null,
    val type: String? = null,
    val contentId: String? = null,
    val commentId: String? = null,
    val message: String? = null,
    val read: Boolean? = null,
    val createdAt: String? = null,
)

data class NotificationActorDto(
    val id: String? = null,
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
)

data class UnreadCountDto(val unreadCount: Long? = null)

data class MarkReadDto(
    val changed: Boolean? = null,
    val unreadCount: Long? = null,
)

data class MarkAllReadDto(
    val changedCount: Long? = null,
    val unreadCount: Long? = null,
)

internal fun CardItemDto.toModel(): CardItem? {
    val contentId = id.orEmpty()
    if (contentId.isBlank()) return null
    return when (type) {
        "video" -> VideoItem(
            id = contentId,
            title = title.orEmpty(),
            author = author.orEmpty(),
            avatarUrl = avatarUrl.orEmpty(),
            videoUrl = videoUrl.orEmpty(),
            coverUrl = coverUrl.orEmpty(),
            coverThumbnailUrl = coverThumbnailUrl.orEmpty(),
            musicUrl = musicUrl,
            likes = likes ?: 0,
            comments = comments ?: 0,
            collections = collections ?: 0,
            shares = shares ?: 0,
            tags = tags.orEmpty(),
            recommendWords = recommendWords.orEmpty(),
            contentSearches = contentSearches.orEmpty(),
            qualityUrls = qualityUrls?.filterValues(String::isNotBlank)?.ifEmpty { null },
            hlsUrl = hlsUrl,
            authorUserId = authorUserId?.takeIf(String::isNotBlank)
                ?: userId?.takeIf(String::isNotBlank),
            likedByViewer = likedByViewer ?: false,
            favoritedByViewer = favoritedByViewer ?: false,
        )
        "image" -> ImageCardItem(
            id = contentId,
            title = title.orEmpty(),
            author = author.orEmpty(),
            imageUrl = imageUrl.orEmpty(),
            likes = likes ?: 0,
            comments = comments ?: 0,
            bgMusicUrl = bgMusicUrl.orEmpty(),
            likedByViewer = likedByViewer ?: false,
            favoritedByViewer = favoritedByViewer ?: false,
        )
        "album" -> AlbumCardItem(
            id = contentId,
            title = title.orEmpty(),
            author = author.orEmpty(),
            avatarUrl = avatarUrl.orEmpty(),
            slides = slides.orEmpty().mapNotNull { slide ->
                val url = slide.mediaUrl.orEmpty()
                if (url.isBlank()) null else AlbumSlide(slide.type ?: 0, url)
            },
            bgMusicUrl = bgMusicUrl.orEmpty(),
            likes = likes ?: 0,
            comments = comments ?: 0,
            shares = shares ?: 0,
            tags = tags.orEmpty(),
            recommendWords = recommendWords.orEmpty(),
            likedByViewer = likedByViewer ?: false,
            favoritedByViewer = favoritedByViewer ?: false,
        )
        else -> null
    }
}

internal fun UserProfileDto.toModel(): SocialProfile = SocialProfile(
    id = id.orEmpty(),
    account = handle.orEmpty(),
    nickname = nickname.orEmpty(),
    bio = bio.orEmpty(),
    region = location.orEmpty(),
    avatarUrl = avatarUrl.orEmpty(),
    worksCount = postCount.asCount(),
    followingCount = followingCount.asCount(),
    followersCount = followerCount.asCount(),
    likesReceivedCount = receivedLikeCount.asCount(),
    isFollowing = following ?: false,
    followsMe = followedBy ?: false,
)

internal fun UserListItemDto.toModel(): SocialProfile = SocialProfile(
    id = id.orEmpty(),
    account = handle.orEmpty(),
    nickname = nickname.orEmpty(),
    bio = bio.orEmpty(),
    region = location.orEmpty(),
    avatarUrl = avatarUrl.orEmpty(),
    worksCount = 0,
    followingCount = followingCount.asCount(),
    followersCount = followerCount.asCount(),
    likesReceivedCount = 0,
    isFollowing = following ?: false,
    followsMe = followedBy ?: false,
)

internal fun CardItemDto.toProfileVideo(fallbackAuthorUserId: String? = null): ProfileVideo? {
    val card = toModel() as? VideoItem ?: return null
    return ProfileVideo(
        id = card.id,
        title = card.title,
        author = card.author,
        coverUrl = card.coverUrl,
        authorUserId = card.authorUserId ?: fallbackAuthorUserId,
    )
}

internal fun ContentStatsDto.toModel(): ContentStats = ContentStats(
    contentId = contentId.orEmpty(),
    likeCount = likeCount ?: 0,
    commentCount = commentCount ?: 0,
    favoriteCount = favoriteCount ?: 0,
    shareCount = shareCount ?: 0,
    viewCount = viewCount ?: 0,
)

internal fun CommentDto.toModel(): ContentComment = ContentComment(
    id = id.orEmpty(),
    contentId = contentId.orEmpty(),
    userId = userId.orEmpty(),
    nickname = nickname.orEmpty(),
    avatarUrl = avatarUrl.orEmpty(),
    parentId = parentId,
    body = body.orEmpty(),
    likeCount = likeCount ?: 0,
    createdAt = createdAt.orEmpty(),
)

internal fun NotificationDto.toModel(): SocialNotification = SocialNotification(
    id = id.orEmpty(),
    actorUserId = actorUserId ?: actor?.userId ?: actor?.id,
    actorNickname = actorNickname ?: actor?.nickname,
    actorAvatarUrl = actorAvatarUrl ?: actor?.avatarUrl,
    type = type.orEmpty(),
    contentId = contentId,
    commentId = commentId,
    message = message.orEmpty(),
    isRead = read ?: false,
    createdAt = createdAt.orEmpty(),
)

private fun Long?.asCount(): Int = (this ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
