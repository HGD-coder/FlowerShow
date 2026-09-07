package com.example.flower_show.data.remote.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Synchronous HTTP boundary used by [DefaultChatRepository][com.example.flower_show.data.repository.DefaultChatRepository]
 * from its IO dispatcher.
 */
class ChatApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val gson: Gson = Gson(),
) {
    private val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    internal fun conversations(
        cursor: String?,
        pageSize: Int,
    ): ChatCursorPageDto<ChatConversationSummaryDto> =
        get(
            segments = listOf("conversations"),
            query = listOf("cursor" to cursor, "pageSize" to pageSize.toString()),
        )

    internal fun createDirect(userId: String): ChatConversationDetailDto =
        post(
            segments = listOf("conversations", "direct"),
            body = CreateDirectConversationRequestDto(userId),
        )

    internal fun createGroup(
        name: String,
        memberUserIds: List<String>,
    ): ChatConversationDetailDto =
        post(
            segments = listOf("conversations", "group"),
            body = CreateGroupConversationRequestDto(name, memberUserIds),
        )

    internal fun conversation(conversationId: String): ChatConversationDetailDto =
        get(listOf("conversations", conversationId))

    internal fun renameGroup(
        conversationId: String,
        name: String,
    ): ChatConversationDetailDto =
        patch(
            segments = listOf("conversations", conversationId),
            body = RenameGroupRequestDto(name),
        )

    internal fun addMembers(
        conversationId: String,
        userIds: List<String>,
    ): ChatConversationDetailDto =
        post(
            segments = listOf("conversations", conversationId, "members"),
            body = AddGroupMembersRequestDto(userIds),
        )

    internal fun removeMember(
        conversationId: String,
        userId: String,
    ): ChatConversationDetailDto =
        delete(listOf("conversations", conversationId, "members", userId))

    internal fun transferOwner(
        conversationId: String,
        userId: String,
    ): ChatConversationDetailDto =
        post(
            segments = listOf("conversations", conversationId, "owner"),
            body = TransferGroupOwnerRequestDto(userId),
        )

    internal fun dissolve(conversationId: String): ChatConversationDetailDto =
        postWithoutBody(listOf("conversations", conversationId, "dissolve"))

    internal fun leave(conversationId: String): LeaveConversationDto =
        postWithoutBody(listOf("conversations", conversationId, "leave"))

    internal fun messages(
        conversationId: String,
        cursor: String?,
        pageSize: Int,
    ): ChatCursorPageDto<RemoteChatMessageDto> =
        get(
            segments = listOf("conversations", conversationId, "messages"),
            query = listOf("cursor" to cursor, "pageSize" to pageSize.toString()),
        )

    internal fun sendText(
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): RemoteChatMessageDto =
        post(
            segments = listOf("conversations", conversationId, "messages"),
            body = SendChatMessageRequestDto(
                type = "text",
                text = text,
                clientMessageId = clientMessageId,
            ),
        )

    internal fun sendVideoShare(
        conversationId: String,
        contentId: String,
        clientMessageId: String,
    ): RemoteChatMessageDto =
        post(
            segments = listOf("conversations", conversationId, "messages"),
            body = SendChatMessageRequestDto(
                type = "video_share",
                contentId = contentId,
                clientMessageId = clientMessageId,
            ),
        )

    internal fun markRead(
        conversationId: String,
        messageId: String?,
    ): ChatReadReceiptDto {
        val segments = listOf("conversations", conversationId, "read")
        return if (messageId == null) {
            postWithoutBody(segments)
        } else {
            post(segments, MarkReadRequestDto(messageId))
        }
    }

    private inline fun <reified T> get(
        segments: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
    ): T {
        val request = Request.Builder()
            .url(url(segments, query))
            .header("Accept", JsonMediaType.toString())
            .get()
            .build()
        return execute(request)
    }

    private inline fun <reified T> post(
        segments: List<String>,
        body: Any,
    ): T =
        execute(
            Request.Builder()
                .url(url(segments))
                .header("Accept", JsonMediaType.toString())
                .post(gson.toJson(body).toRequestBody(JsonMediaType))
                .build(),
        )

    private inline fun <reified T> postWithoutBody(
        segments: List<String>,
    ): T =
        execute(
            Request.Builder()
                .url(url(segments))
                .header("Accept", JsonMediaType.toString())
                .post(EmptyRequestBody)
                .build(),
        )

    private inline fun <reified T> patch(
        segments: List<String>,
        body: Any,
    ): T =
        execute(
            Request.Builder()
                .url(url(segments))
                .header("Accept", JsonMediaType.toString())
                .patch(gson.toJson(body).toRequestBody(JsonMediaType))
                .build(),
        )

    private inline fun <reified T> delete(segments: List<String>): T =
        execute(
            Request.Builder()
                .url(url(segments))
                .header("Accept", JsonMediaType.toString())
                .delete()
                .build(),
        )

    private fun url(
        segments: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
    ): HttpUrl = baseHttpUrl.newBuilder().apply {
        addPathSegment("chat")
        segments.forEach(::addPathSegment)
        query.forEach { (name, value) ->
            if (value != null) addQueryParameter(name, value)
        }
    }.build()

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ChatApiException(
                    statusCode = response.code,
                    message = parseErrorMessage(response.code, body),
                )
            }
            return runCatching {
                gson.fromJson<T>(body, object : TypeToken<T>() {}.type)
                    ?: error("Response body was null")
            }.getOrElse { cause ->
                throw ChatApiException(
                    statusCode = response.code,
                    message = "Invalid chat API response: ${cause.message}",
                    cause = cause,
                )
            }
        }
    }

    private fun parseErrorMessage(statusCode: Int, body: String): String {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        val error = root?.get("error")
        return root.string("message")
            ?: root.string("detail")
            ?: error?.takeIf { it.isJsonPrimitive }?.asString
            ?: error?.takeIf { it.isJsonObject }?.asJsonObject.string("message")
            ?: "Chat API request failed with HTTP $statusCode"
    }

    private fun com.google.gson.JsonObject?.string(name: String): String? =
        this?.get(name)
            ?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }
            ?.asString
            ?.takeIf(String::isNotBlank)

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        val EmptyRequestBody: RequestBody = ByteArray(0).toRequestBody()
    }
}

class ChatApiException(
    val statusCode: Int,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
