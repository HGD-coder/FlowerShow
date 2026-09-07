package com.example.flower_show.data.repository

import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatCursorPage
import com.example.flower_show.model.ChatReadReceipt
import com.example.flower_show.model.RemoteChatMessage

interface IChatRepository {
    suspend fun listConversations(
        cursor: String? = null,
        pageSize: Int = 20,
    ): Result<ChatCursorPage<ChatConversationSummary>>

    suspend fun createOrResolveDirectConversation(
        userId: String,
    ): Result<ChatConversationDetail>

    suspend fun createGroupConversation(
        name: String,
        memberUserIds: List<String> = emptyList(),
    ): Result<ChatConversationDetail>

    suspend fun getConversation(
        conversationId: String,
    ): Result<ChatConversationDetail>

    suspend fun renameGroup(
        conversationId: String,
        name: String,
    ): Result<ChatConversationDetail>

    suspend fun addMembers(
        conversationId: String,
        userIds: List<String>,
    ): Result<ChatConversationDetail>

    suspend fun removeMember(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail>

    suspend fun transferGroupOwner(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail>

    suspend fun dissolveGroup(
        conversationId: String,
    ): Result<ChatConversationDetail>

    suspend fun leaveConversation(
        conversationId: String,
    ): Result<Boolean>

    suspend fun listMessages(
        conversationId: String,
        cursor: String? = null,
        pageSize: Int = 50,
    ): Result<ChatCursorPage<RemoteChatMessage>>

    suspend fun sendText(
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage>

    suspend fun sendVideoShare(
        conversationId: String,
        contentId: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage>

    suspend fun markRead(
        conversationId: String,
        messageId: String? = null,
    ): Result<ChatReadReceipt>
}
