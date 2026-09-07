package com.example.flower_show

import com.example.flower_show.data.repository.IChatRepository
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatCursorPage
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.ChatReadReceipt
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

internal data class InstrumentedVideoRequest(
    val conversationId: String,
    val contentId: String,
    val clientMessageId: String,
)

internal class TestChatRepository : IChatRepository {
    private val sequence = AtomicLong(10)
    private val messages = ConcurrentHashMap<String, CopyOnWriteArrayList<RemoteChatMessage>>()
    private var groupDetail: ChatConversationDetail? = null
    val videoRequests = CopyOnWriteArrayList<InstrumentedVideoRequest>()

    override suspend fun listConversations(
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<ChatConversationSummary>> {
        val conversationId = "friend-linwu"
        return Result.success(
            ChatCursorPage(
                items = listOf(
                    ChatConversationSummary(
                        conversationId = conversationId,
                        type = ChatConversationType.DIRECT,
                        name = null,
                        displayName = "林雾",
                        displayAvatarUrl = null,
                        ownerUserId = null,
                        state = ChatConversationState.ACTIVE,
                        dissolvedAt = null,
                        lastMessage = messages[conversationId]?.lastOrNull(),
                        unreadCount = 2,
                        updatedAt = "10:32",
                        avatarMembers = emptyList(),
                    ),
                ),
                nextCursor = null,
                hasMore = false,
            ),
        )
    }

    override suspend fun createOrResolveDirectConversation(
        userId: String,
    ): Result<ChatConversationDetail> = Result.success(directDetail(userId))

    override suspend fun createGroupConversation(
        name: String,
        memberUserIds: List<String>,
    ): Result<ChatConversationDetail> {
        groupDetail = groupDetail(name, "instrumented-user-id", memberUserIds)
        return Result.success(groupDetail!!)
    }

    override suspend fun getConversation(
        conversationId: String,
    ): Result<ChatConversationDetail> = Result.success(
        groupDetail?.takeIf { it.conversationId == conversationId }
            ?: directDetail(conversationId),
    )

    override suspend fun renameGroup(
        conversationId: String,
        name: String,
    ): Result<ChatConversationDetail> =
        updateGroup { it.copy(name = name) }

    override suspend fun addMembers(
        conversationId: String,
        userIds: List<String>,
    ): Result<ChatConversationDetail> = updateGroup { detail ->
        val existing = detail.members.mapTo(mutableSetOf()) { it.userId }
        detail.copy(
            members = detail.members + userIds.filterNot { it in existing }.map(::member),
        )
    }

    override suspend fun removeMember(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> =
        updateGroup { it.copy(members = it.members.filterNot { member -> member.userId == userId }) }

    override suspend fun transferGroupOwner(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> = updateGroup { detail ->
        detail.copy(
            ownerUserId = userId,
            members = detail.members.map {
                it.copy(
                    role = if (it.userId == userId) ChatMemberRole.OWNER
                    else ChatMemberRole.MEMBER,
                )
            },
        )
    }

    override suspend fun dissolveGroup(
        conversationId: String,
    ): Result<ChatConversationDetail> =
        updateGroup { it.copy(state = ChatConversationState.DISSOLVED) }

    override suspend fun leaveConversation(conversationId: String): Result<Boolean> =
        Result.success(true)

    override suspend fun listMessages(
        conversationId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<RemoteChatMessage>> = Result.success(
        ChatCursorPage(
            items = messages[conversationId].orEmpty().asReversed(),
            nextCursor = null,
            hasMore = false,
        ),
    )

    override suspend fun sendText(
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> {
        val message = message(
            conversationId = conversationId,
            senderId = "instrumented-user-id",
            text = text,
            clientMessageId = clientMessageId,
        )
        messages.computeIfAbsent(conversationId) { CopyOnWriteArrayList() }.add(message)
        return Result.success(message)
    }

    override suspend fun sendVideoShare(
        conversationId: String,
        contentId: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> {
        videoRequests += InstrumentedVideoRequest(conversationId, contentId, clientMessageId)
        val message = message(
            conversationId = conversationId,
            senderId = "instrumented-user-id",
            text = null,
            clientMessageId = clientMessageId,
            type = RemoteChatMessageType.VIDEO_SHARE,
            contentId = contentId,
        )
        messages.computeIfAbsent(conversationId) { CopyOnWriteArrayList() }.add(message)
        return Result.success(message)
    }

    override suspend fun markRead(
        conversationId: String,
        messageId: String?,
    ): Result<ChatReadReceipt> = Result.success(
        ChatReadReceipt(
            conversationId = conversationId,
            lastReadMessageId = messageId,
            readAt = "now",
            unreadCount = 0,
        ),
    )

    private fun updateGroup(
        transform: (ChatConversationDetail) -> ChatConversationDetail,
    ): Result<ChatConversationDetail> {
        val current = groupDetail ?: groupDetail(
            name = "测试群",
            ownerId = "instrumented-user-id",
            memberUserIds = listOf("friend-linwu"),
        )
        return transform(current).also { groupDetail = it }.let(Result.Companion::success)
    }

    private fun directDetail(userId: String) = ChatConversationDetail(
        conversationId = userId,
        type = ChatConversationType.DIRECT,
        name = null,
        ownerUserId = null,
        state = ChatConversationState.ACTIVE,
        dissolvedAt = null,
        dissolvedByUserId = null,
        members = listOf(member("instrumented-user-id"), member(userId)),
        createdAt = "created",
        updatedAt = "updated",
    )

    private fun groupDetail(
        name: String,
        ownerId: String,
        memberUserIds: List<String>,
    ) = ChatConversationDetail(
        conversationId = "instrumented-group",
        type = ChatConversationType.GROUP,
        name = name,
        ownerUserId = ownerId,
        state = ChatConversationState.ACTIVE,
        dissolvedAt = null,
        dissolvedByUserId = null,
        members = (listOf(ownerId) + memberUserIds).distinct().map {
            member(
                it,
                if (it == ownerId) ChatMemberRole.OWNER else ChatMemberRole.MEMBER,
            )
        },
        createdAt = "created",
        updatedAt = "updated",
    )

    private fun member(
        userId: String,
        role: ChatMemberRole = ChatMemberRole.MEMBER,
    ) = ChatMember(
        userId = userId,
        nickname = if (userId == "friend-linwu") "林雾" else userId,
        avatarUrl = null,
        role = role,
        joinedAt = "joined",
    )

    private fun message(
        conversationId: String,
        senderId: String,
        text: String?,
        clientMessageId: String,
        type: RemoteChatMessageType = RemoteChatMessageType.TEXT,
        contentId: String? = null,
    ) = RemoteChatMessage(
        messageId = "instrumented-message-${sequence.incrementAndGet()}",
        conversationId = conversationId,
        senderUserId = senderId,
        senderNickname = if (senderId == "instrumented-user-id") "测试用户" else "林雾",
        senderAvatarUrl = null,
        type = type,
        text = text,
        sharedContentId = contentId,
        videoPreview = null,
        clientMessageId = clientMessageId,
        createdAt = "刚刚",
    )
}
