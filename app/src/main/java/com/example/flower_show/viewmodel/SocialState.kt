package com.example.flower_show.viewmodel

import androidx.compose.runtime.Immutable
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile

enum class ProfileContentTab {
    Works,
    Likes,
    Collections,
}

enum class RelationListTab {
    Following,
    Followers,
}

enum class MessageListTab {
    Notifications,
    Chats,
}

@Immutable
data class SocialState(
    val myProfile: SocialProfile = SocialProfile.Empty,
    /** Network-backed profile cache, including relation rows and opened profiles. */
    val people: List<SocialProfile> = emptyList(),
    val selectedProfileTab: ProfileContentTab = ProfileContentTab.Works,
    val selectedRelationTab: RelationListTab = RelationListTab.Following,
    val relationQuery: String = "",
    /** The currently selected backend following/follower search result. */
    val relationProfiles: List<SocialProfile> = emptyList(),
    val selectedMessageTab: MessageListTab = MessageListTab.Chats,
    val notifications: List<SocialNotification> = emptyList(),
    val notificationUnreadCount: Int = 0,
    val notificationsNextCursor: String? = null,
    val notificationsHasMore: Boolean = true,
    val isProfileLoading: Boolean = false,
    val isRelationLoading: Boolean = false,
    val isNotificationsLoading: Boolean = false,
    val error: String? = null,
) {
    fun profile(userId: String): SocialProfile? =
        people.firstOrNull { it.id == userId }
}
