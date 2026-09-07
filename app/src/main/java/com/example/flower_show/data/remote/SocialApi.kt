package com.example.flower_show.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Synchronous HTTP API used from repository IO dispatchers.
 *
 * The supplied client must be AuthGraph's authenticated client in production so
 * access-token attachment and refresh behavior stay centralized.
 */
class SocialApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val gson: Gson = Gson(),
) {
    private val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    fun feed(page: Int, pageSize: Int): List<CardItemDto> =
        get(
            segments = listOf("feed"),
            query = listOf("page" to page.toString(), "pageSize" to pageSize.toString()),
        )

    fun search(keyword: String): List<CardItemDto> =
        get(listOf("search"), listOf("keyword" to keyword))

    fun followingFeed(cursor: String?, pageSize: Int): CursorPageDto<CardItemDto> =
        get(
            segments = listOf("feed", "following"),
            query = listOf("cursor" to cursor, "pageSize" to pageSize.toString()),
        )

    fun profile(userId: String): UserProfileDto =
        get(listOf("users", userId, "profile"))

    fun profileContents(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ): PageDto<CardItemDto> =
        get(
            segments = listOf("users", userId, "profile", "contents"),
            query = listOf(
                "tab" to tab,
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
            ),
        )

    fun relations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ): PageDto<UserListItemDto> =
        get(
            segments = listOf("users", userId, relation),
            query = listOf(
                "keyword" to keyword,
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
            ),
        )

    fun follow(actorUserId: String, targetUserId: String): FollowActionResultDto =
        post(listOf("users", actorUserId, "following", targetUserId))

    fun unfollow(actorUserId: String, targetUserId: String): FollowActionResultDto =
        delete(listOf("users", actorUserId, "following", targetUserId))

    fun updateProfileDisplay(
        userId: String,
        contentId: String,
        visible: Boolean,
    ): ProfileContentDisplayDto =
        patch(
            segments = listOf("users", userId, "contents", contentId, "profile-display"),
            body = mapOf("showOnProfile" to visible),
        )

    fun setLiked(contentId: String, userId: String, liked: Boolean): InteractionResultDto {
        val segments = listOf("contents", contentId, "like")
        return if (liked) {
            post(segments, mapOf("userId" to userId))
        } else {
            delete(segments)
        }
    }

    fun setFavorited(contentId: String, userId: String, favorited: Boolean): InteractionResultDto {
        val segments = listOf("contents", contentId, "favorite")
        return if (favorited) {
            post(segments, mapOf("userId" to userId))
        } else {
            delete(segments)
        }
    }

    fun comments(contentId: String): List<CommentDto> =
        get(listOf("contents", contentId, "comments"))

    fun createComment(contentId: String, userId: String, body: String): CommentDto =
        post(
            segments = listOf("contents", contentId, "comments"),
            body = mapOf(
                "userId" to userId,
                "parentId" to null,
                "body" to body,
            ),
        )

    fun notifications(cursor: String?, pageSize: Int): CursorPageDto<NotificationDto> =
        get(
            segments = listOf("me", "notifications"),
            query = listOf("cursor" to cursor, "pageSize" to pageSize.toString()),
        )

    fun notificationUnreadCount(): UnreadCountDto =
        get(listOf("me", "notifications", "unread-count"))

    fun markNotificationRead(notificationId: String): MarkReadDto =
        post(listOf("me", "notifications", notificationId, "read"))

    fun markAllNotificationsRead(): MarkAllReadDto =
        post(listOf("me", "notifications", "read-all"))

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
        body: Any? = null,
    ): T {
        val json = body?.let(gson::toJson).orEmpty().ifEmpty { "{}" }
        val request = Request.Builder()
            .url(url(segments))
            .header("Accept", JsonMediaType.toString())
            .post(json.toRequestBody(JsonMediaType))
            .build()
        return execute(request)
    }

    private inline fun <reified T> patch(
        segments: List<String>,
        body: Any,
    ): T {
        val request = Request.Builder()
            .url(url(segments))
            .header("Accept", JsonMediaType.toString())
            .patch(gson.toJson(body).toRequestBody(JsonMediaType))
            .build()
        return execute(request)
    }

    private inline fun <reified T> delete(segments: List<String>): T {
        val request = Request.Builder()
            .url(url(segments))
            .header("Accept", JsonMediaType.toString())
            .delete()
            .build()
        return execute(request)
    }

    private fun url(
        segments: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
    ): HttpUrl = baseHttpUrl.newBuilder().apply {
        segments.forEach(::addPathSegment)
        query.forEach { (name, value) ->
            if (value != null) addQueryParameter(name, value)
        }
    }.build()

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SocialApiException(
                    statusCode = response.code,
                    message = parseErrorMessage(response.code, body),
                )
            }
            return runCatching {
                gson.fromJson<T>(body, object : TypeToken<T>() {}.type)
            }.getOrElse {
                throw SocialApiException(response.code, "Invalid social API response")
            }
        }
    }

    private fun parseErrorMessage(statusCode: Int, body: String): String {
        val parsed = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return parsed?.get("message")?.takeUnless { it.isJsonNull }?.asString
            ?: parsed?.get("error")?.takeUnless { it.isJsonNull }?.asString
            ?: "Social API request failed with HTTP $statusCode"
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

class SocialApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
