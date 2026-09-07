package com.example.flower_show.data.repository

import com.example.flower_show.data.remote.chat.ChatApi
import com.example.flower_show.data.remote.chat.toDomain
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatCursorPage
import com.example.flower_show.model.ChatReadReceipt
import com.example.flower_show.model.RemoteChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultChatRepository(
    private val api: ChatApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IChatRepository {

    override suspend fun listConversations(
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<ChatConversationSummary>> = remoteResult {
        val response = api.conversations(cursor, pageSize)
        ChatCursorPage(
            items = response.items.orEmpty().map { it.toDomain() },
            nextCursor = response.nextCursor,
            hasMore = response.hasMore == true && !response.nextCursor.isNullOrBlank(),
        )
    }

    override suspend fun createOrResolveDirectConversation(
        userId: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.createDirect(userId).toDomain()
    }

    override suspend fun createGroupConversation(
        name: String,
        memberUserIds: List<String>,
    ): Result<ChatConversationDetail> = remoteResult {
        api.createGroup(name, memberUserIds).toDomain()
    }

    override suspend fun getConversation(
        conversationId: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.conversation(conversationId).toDomain()
    }

    override suspend fun renameGroup(
        conversationId: String,
        name: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.renameGroup(conversationId, name).toDomain()
    }

    override suspend fun addMembers(
        conversationId: String,
        userIds: List<String>,
    ): Result<ChatConversationDetail> = remoteResult {
        api.addMembers(conversationId, userIds).toDomain()
    }

    override suspend fun removeMember(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.removeMember(conversationId, userId).toDomain()
    }

    override suspend fun transferGroupOwner(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.transferOwner(conversationId, userId).toDomain()
    }

    override suspend fun dissolveGroup(
        conversationId: String,
    ): Result<ChatConversationDetail> = remoteResult {
        api.dissolve(conversationId).toDomain()
    }

    override suspend fun leaveConversation(
        conversationId: String,
    ): Result<Boolean> = remoteResult {
        api.leave(conversationId).changed ?: false
    }

    override suspend fun listMessages(
        conversationId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<RemoteChatMessage>> = remoteResult {
        val response = api.messages(conversationId, cursor, pageSize)
        ChatCursorPage(
            items = response.items.orEmpty().map { it.toDomain(conversationId) },
            nextCursor = response.nextCursor,
            hasMore = response.hasMore == true && !response.nextCursor.isNullOrBlank(),
        )
    }

    override suspend fun sendText(
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> = remoteResult {
        api.sendText(conversationId, text, clientMessageId).toDomain(conversationId)
    }

    override suspend fun sendVideoShare(
        conversationId: String,
        contentId: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> = remoteResult {
        api.sendVideoShare(conversationId, contentId, clientMessageId)
            .toDomain(conversationId)
    }

    override suspend fun markRead(
        conversationId: String,
        messageId: String?,
    ): Result<ChatReadReceipt> = remoteResult {
        api.markRead(conversationId, messageId).toDomain(conversationId)
    }

    private suspend fun <T> remoteResult(action: () -> T): Result<T> =
        withContext(ioDispatcher) {
            runCatching(action)
        }
}
