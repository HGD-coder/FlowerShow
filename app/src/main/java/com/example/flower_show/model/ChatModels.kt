package com.example.flower_show.model

import androidx.compose.runtime.Immutable

enum class ChatConversationType(val wireValue: String) {
    DIRECT("direct"),
    GROUP("group"),
    UNKNOWN("unknown"),
    ;

    companion object {
        internal fun fromWireValue(value: String?): ChatConversationType =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class ChatConversationState(val wireValue: String) {
    ACTIVE("active"),
    DISSOLVED("dissolved"),
    UNKNOWN("unknown"),
    ;

    companion object {
        internal fun fromWireValue(value: String?): ChatConversationState =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class ChatMemberRole(val wireValue: String) {
    OWNER("owner"),
    MEMBER("member"),
    UNKNOWN("unknown"),
    ;

    companion object {
        internal fun fromWireValue(value: String?): ChatMemberRole =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class RemoteChatMessageType(val wireValue: String) {
    TEXT("text"),
    VIDEO_SHARE("video_share"),
    UNKNOWN("unknown"),
    ;

    companion object {
        internal fun fromWireValue(value: String?): RemoteChatMessageType =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

@Immutable
data class ChatCursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

@Immutable
data class ChatVideoPreview(
    val contentId: String?,
    val title: String?,
    val authorUserId: String?,
    val authorNickname: String?,
    val authorAvatarUrl: String?,
    val coverUrl: String?,
    val coverThumbnailUrl: String?,
    val videoUrl: String?,
    val hlsUrl: String?,
)

@Immutable
data class RemoteChatMessage(
    val messageId: String,
    val conversationId: String,
    val senderUserId: String,
    val senderNickname: String?,
    val senderAvatarUrl: String?,
    val type: RemoteChatMessageType,
    val text: String?,
    val sharedContentId: String?,
    val videoPreview: ChatVideoPreview?,
    val clientMessageId: String,
    val createdAt: String,
)

@Immutable
data class ChatConversationSummary(
    val conversationId: String,
    val type: ChatConversationType,
    val name: String?,
    val displayName: String?,
    val displayAvatarUrl: String?,
    val ownerUserId: String?,
    val state: ChatConversationState,
    val dissolvedAt: String?,
    val lastMessage: RemoteChatMessage?,
    val unreadCount: Long,
    val updatedAt: String,
    val avatarMembers: List<ChatAvatarMember> = emptyList(),
)

@Immutable
data class ChatAvatarMember(
    val userId: String,
    val nickname: String?,
    val avatarUrl: String?,
)

@Immutable
data class ChatMember(
    val userId: String,
    val nickname: String?,
    val avatarUrl: String?,
    val role: ChatMemberRole,
    val joinedAt: String,
)

@Immutable
data class ChatConversationDetail(
    val conversationId: String,
    val type: ChatConversationType,
    val name: String?,
    val ownerUserId: String?,
    val state: ChatConversationState,
    val dissolvedAt: String?,
    val dissolvedByUserId: String?,
    val members: List<ChatMember>,
    val createdAt: String,
    val updatedAt: String,
)

@Immutable
data class ChatReadReceipt(
    val conversationId: String,
    val lastReadMessageId: String?,
    val readAt: String,
    val unreadCount: Long,
)
