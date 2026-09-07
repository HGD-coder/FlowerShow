package com.example.flower_show.viewmodel

import com.example.flower_show.data.auth.AuthUser

sealed interface SocialIntent {
    data class BindAuthenticatedUser(val user: AuthUser) : SocialIntent
    data class LoadProfile(val userId: String) : SocialIntent
    data class SelectProfileTab(val tab: ProfileContentTab) : SocialIntent
    data class SelectRelationTab(val tab: RelationListTab) : SocialIntent
    data class UpdateRelationQuery(val query: String) : SocialIntent
    data class ToggleFollow(val userId: String) : SocialIntent
    data class SetWorkVisibility(val videoId: String, val visible: Boolean) : SocialIntent
    data class SelectMessageTab(val tab: MessageListTab) : SocialIntent
    data object LoadNotifications : SocialIntent
    data object LoadMoreNotifications : SocialIntent
    data class MarkNotificationRead(val notificationId: String) : SocialIntent
    data object MarkAllNotificationsRead : SocialIntent
    data object DismissError : SocialIntent

    /** 登出时清空内存中的社交状态（个人资料、关注列表、通知等）。 */
    data object UnbindUser : SocialIntent
}
