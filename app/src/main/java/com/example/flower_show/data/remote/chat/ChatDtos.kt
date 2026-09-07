package com.example.flower_show.data.remote.chat

import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.ChatReadReceipt
import com.example.flower_show.model.ChatVideoPreview
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import com.google.gson.annotations.SerializedName

internal data class ChatCursorPageDto<T>(
    val items: List<T>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
)

internal data class ChatConversationSummaryDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val displayAvatarUrl: String? = null,
    val ownerUserId: String? = null,
    val state: String? = null,
    val dissolvedAt: String? = null,
    val lastMessage: RemoteChatMessageDto? = null,
    val unreadCount: Long? = null,
    val updatedAt: String? = null,
    val avatarMembers: List<ChatAvatarMemberDto>? = null,
)

internal data class ChatAvatarMemberDto(
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
)

internal data class ChatConversationDetailDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val ownerUserId: String? = null,
    val state: String? = null,
    val dissolvedAt: String? = null,
    val dissolvedByUserId: String? = null,
    val members: List<ChatMemberDto>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

internal data class ChatMemberDto(
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null,
    val joinedAt: String? = null,
)

internal data class RemoteChatMessageDto(
    val id: String? = null,
    val conversationId: String? = null,
    val senderUserId: String? = null,
    val senderNickname: String? = null,
    val senderAvatarUrl: String? = null,
    val type: String? = null,
    val text: String? = null,
    val sharedContentId: String? = null,
    @SerializedName(value = "sharedContent", alternate = ["videoPreview"])
    val videoPreview: ChatVideoPreviewDto? = null,
    val clientMessageId: String? = null,
    val createdAt: String? = null,
)

internal data class ChatVideoPreviewDto(
    @SerializedName(value = "contentId", alternate = ["id"])
    val contentId: String? = null,
    val title: String? = null,
    val authorUserId: String? = null,
    @SerializedName(value = "authorNickname", alternate = ["author"])
    val authorNickname: String? = null,
    val authorAvatarUrl: String? = null,
    val coverUrl: String? = null,
    val coverThumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val hlsUrl: String? = null,
)

internal data class ChatReadReceiptDto(
    val conversationId: String? = null,
    val lastReadMessageId: String? = null,
    val readAt: String? = null,
    val unreadCount: Long? = null,
)

internal data class LeaveConversationDto(
    val changed: Boolean? = null,
)

internal data class CreateDirectConversationRequestDto(
    val userId: String,
)

internal data class CreateGroupConversationRequestDto(
    val name: String,
    val memberUserIds: List<String>,
)

internal data class RenameGroupRequestDto(
    val name: String,
)

internal data class AddGroupMembersRequestDto(
    val userIds: List<String>,
)

internal data class TransferGroupOwnerRequestDto(
    val userId: String,
)

internal data class SendChatMessageRequestDto(
    val type: String,
    val text: String? = null,
    val contentId: String? = null,
    val clientMessageId: String,
)

internal data class MarkReadRequestDto(
    val messageId: String,
)

internal fun ChatConversationSummaryDto.toDomain(): ChatConversationSummary {
    val conversationId = id.required("conversation.id")
    return ChatConversationSummary(
        conversationId = conversationId,
        type = ChatConversationType.fromWireValue(type),
        name = name,
        displayName = displayName,
        displayAvatarUrl = displayAvatarUrl,
        ownerUserId = ownerUserId,
        state = ChatConversationState.fromWireValue(state),
        dissolvedAt = dissolvedAt,
        lastMessage = lastMessage?.toDomain(conversationId),
        unreadCount = unreadCount.orZero(),
        updatedAt = updatedAt.required("conversation.updatedAt"),
        avatarMembers = avatarMembers.orEmpty()
            .take(MaxAvatarMembers)
            .map(ChatAvatarMemberDto::toDomain),
    )
}

internal fun ChatAvatarMemberDto.toDomain(): ChatAvatarMember =
    ChatAvatarMember(
        userId = userId.required("conversation.avatarMembers.userId"),
        nickname = nickname,
        avatarUrl = avatarUrl,
    )

internal fun ChatConversationDetailDto.toDomain(): ChatConversationDetail =
    ChatConversationDetail(
        conversationId = id.required("conversation.id"),
        type = ChatConversationType.fromWireValue(type),
        name = name,
        ownerUserId = ownerUserId,
        state = ChatConversationState.fromWireValue(state),
        dissolvedAt = dissolvedAt,
        dissolvedByUserId = dissolvedByUserId,
        members = members.orEmpty().map(ChatMemberDto::toDomain),
        createdAt = createdAt.required("conversation.createdAt"),
        updatedAt = updatedAt.required("conversation.updatedAt"),
    )

internal fun ChatMemberDto.toDomain(): ChatMember =
    ChatMember(
        userId = userId.required("member.userId"),
        nickname = nickname,
        avatarUrl = avatarUrl,
        role = ChatMemberRole.fromWireValue(role),
        joinedAt = joinedAt.required("member.joinedAt"),
    )

internal fun RemoteChatMessageDto.toDomain(
    fallbackConversationId: String? = null,
): RemoteChatMessage {
    val sharedContentId = sharedContentId
    return RemoteChatMessage(
        messageId = id.required("message.id"),
        conversationId = conversationId.orIfBlank(fallbackConversationId)
            .required("message.conversationId"),
        senderUserId = senderUserId.required("message.senderUserId"),
        senderNickname = senderNickname,
        senderAvatarUrl = senderAvatarUrl,
        type = RemoteChatMessageType.fromWireValue(type),
        text = text,
        sharedContentId = sharedContentId,
        videoPreview = videoPreview?.toDomain(sharedContentId),
        clientMessageId = clientMessageId.required("message.clientMessageId"),
        createdAt = createdAt.required("message.createdAt"),
    )
}

internal fun ChatVideoPreviewDto.toDomain(
    fallbackContentId: String?,
): ChatVideoPreview =
    ChatVideoPreview(
        contentId = contentId.orIfBlank(fallbackContentId),
        title = title,
        authorUserId = authorUserId,
        authorNickname = authorNickname,
        authorAvatarUrl = authorAvatarUrl,
        coverUrl = coverUrl,
        coverThumbnailUrl = coverThumbnailUrl,
        videoUrl = videoUrl,
        hlsUrl = hlsUrl,
    )

internal fun ChatReadReceiptDto.toDomain(
    fallbackConversationId: String,
): ChatReadReceipt =
    ChatReadReceipt(
        conversationId = conversationId.orIfBlank(fallbackConversationId)
            .required("readReceipt.conversationId"),
        lastReadMessageId = lastReadMessageId,
        readAt = readAt.required("readReceipt.readAt"),
        unreadCount = unreadCount.orZero(),
    )

private fun String?.required(field: String): String =
    this?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Invalid chat API response: missing $field")

private fun String?.orIfBlank(fallback: String?): String? =
    takeIf { !it.isNullOrBlank() } ?: fallback

private fun Long?.orZero(): Long = (this ?: 0L).coerceAtLeast(0L)

private const val MaxAvatarMembers = 4
