package com.example.flower_show.data.repository

import com.example.flower_show.data.remote.chat.ChatApi
import com.example.flower_show.data.remote.chat.ChatApiException
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.RemoteChatMessageType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun conversationCursorMapsRemoteIdentityAndNullableVideoPreview() = runTest {
        server.enqueue(
            json(
                """
                {
                  "items":[{
                    "id":"con-1",
                    "type":"direct",
                    "displayName":"小花",
                    "displayAvatarUrl":null,
                    "state":"active",
                    "dissolvedAt":null,
                    "lastMessage":{
                      "id":"msg-2",
                      "senderUserId":"user-2",
                      "senderNickname":"小花",
                      "senderAvatarUrl":null,
                      "type":"video_share",
                      "text":null,
                      "sharedContentId":"video-9",
                      "sharedContent":{
                        "id":"video-9",
                        "title":"夏日花园",
                        "authorNickname":"园丁",
                        "coverUrl":"https://cdn.example/cover.jpg",
                        "videoUrl":null
                      },
                      "clientMessageId":"client-2",
                      "createdAt":"2026-07-24T09:00:00Z"
                    },
                    "unreadCount":2,
                    "updatedAt":"2026-07-24T09:00:00Z"
                  }],
                  "nextCursor":"next cursor",
                  "hasMore":true
                }
                """,
            ),
        )

        val page = repository().listConversations(
            cursor = "older+cursor",
            pageSize = 7,
        ).getOrThrow()

        val request = server.takeRequest()
        assertEquals(
            "/api/v1/chat/conversations?cursor=older%2Bcursor&pageSize=7",
            request.path,
        )
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals("next cursor", page.nextCursor)
        assertTrue(page.hasMore)
        val conversation = page.items.single()
        assertEquals("con-1", conversation.conversationId)
        assertEquals(ChatConversationType.DIRECT, conversation.type)
        assertEquals(ChatConversationState.ACTIVE, conversation.state)
        assertNull(conversation.displayAvatarUrl)
        assertTrue(conversation.avatarMembers.isEmpty())
        val message = requireNotNull(conversation.lastMessage)
        assertEquals("con-1", message.conversationId)
        assertEquals("小花", message.senderNickname)
        assertNull(message.senderAvatarUrl)
        assertEquals(RemoteChatMessageType.VIDEO_SHARE, message.type)
        val preview = requireNotNull(message.videoPreview)
        assertEquals("video-9", preview.contentId)
        assertEquals("园丁", preview.authorNickname)
        assertNull(preview.videoUrl)
    }

    @Test
    fun conversationAvatarMembersMapInServerOrder() = runTest {
        server.enqueue(
            json(
                """
                {
                  "items":[{
                    "id":"con-group",
                    "type":"group",
                    "displayName":"花友群",
                    "displayAvatarUrl":null,
                    "state":"active",
                    "avatarMembers":[
                      {
                        "userId":"owner",
                        "nickname":"群主",
                        "avatarUrl":"https://cdn.example/owner.jpg"
                      },
                      {"userId":"member-2","nickname":"林","avatarUrl":null},
                      {"userId":"member-3","nickname":"叶","avatarUrl":null},
                      {"userId":"member-4","nickname":"雨","avatarUrl":null}
                    ],
                    "unreadCount":0,
                    "updatedAt":"2026-07-30T08:00:00Z"
                  }],
                  "nextCursor":null,
                  "hasMore":false
                }
                """,
            ),
        )

        val conversation = repository()
            .listConversations(cursor = null, pageSize = 20)
            .getOrThrow()
            .items
            .single()

        assertEquals(
            listOf("owner", "member-2", "member-3", "member-4"),
            conversation.avatarMembers.map { it.userId },
        )
        assertEquals("群主", conversation.avatarMembers.first().nickname)
        assertEquals(
            "https://cdn.example/owner.jpg",
            conversation.avatarMembers.first().avatarUrl,
        )
    }

    @Test
    fun conversationOperationsUseContractMethodsUrlsAndBodies() = runTest {
        repeat(8) { server.enqueue(json(groupDetail())) }
        server.enqueue(json("""{"changed":false}"""))
        val repository = repository()

        repository.createOrResolveDirectConversation("user-b").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/direct", request.path)
            assertEquals("user-b", request.jsonBody()["userId"].asString)
        }

        val created = repository.createGroupConversation(
            name = "花友群",
            memberUserIds = listOf("user-b", "user-c"),
        ).getOrThrow()
        assertEquals("con-group", created.conversationId)
        assertEquals(ChatMemberRole.OWNER, created.members.single().role)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/group", request.path)
            val body = request.jsonBody()
            assertEquals("花友群", body["name"].asString)
            assertEquals(
                listOf("user-b", "user-c"),
                body["memberUserIds"].asJsonArray.map { it.asString },
            )
        }

        repository.getConversation("con-group").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/v1/chat/conversations/con-group", request.path)
        }

        repository.renameGroup("con-group", "新名字").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/chat/conversations/con-group", request.path)
            assertEquals("新名字", request.jsonBody()["name"].asString)
        }

        repository.addMembers("con-group", listOf("user-d")).getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-group/members", request.path)
            assertEquals(
                listOf("user-d"),
                request.jsonBody()["userIds"].asJsonArray.map { it.asString },
            )
        }

        repository.removeMember("con-group", "user-d").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("DELETE", request.method)
            assertEquals(
                "/api/v1/chat/conversations/con-group/members/user-d",
                request.path,
            )
            assertEquals(0L, request.bodySize)
        }

        repository.transferGroupOwner("con-group", "user-b").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-group/owner", request.path)
            assertEquals("user-b", request.jsonBody()["userId"].asString)
        }

        repository.dissolveGroup("con-group").getOrThrow()
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-group/dissolve", request.path)
            assertEquals(0L, request.bodySize)
        }

        val changed = repository.leaveConversation("con-group").getOrThrow()
        assertFalse(changed)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-group/leave", request.path)
            assertEquals(0L, request.bodySize)
        }
    }

    @Test
    fun messageCursorAndSendShapesMapTextAndVideoShare() = runTest {
        server.enqueue(
            json(
                """
                {
                  "items":[{
                    "id":"msg-video",
                    "conversationId":"con-1",
                    "senderUserId":"user-b",
                    "type":"video_share",
                    "sharedContentId":"video-1",
                    "sharedContent":{
                      "id":"video-1",
                      "title":"花海",
                      "coverThumbnailUrl":"thumb"
                    },
                    "clientMessageId":"history-video",
                    "createdAt":"2026-07-24T10:00:00Z"
                  }],
                  "nextCursor":"older",
                  "hasMore":true
                }
                """,
            ),
        )
        server.enqueue(json(textMessage()))
        server.enqueue(json(videoMessage()))
        val repository = repository()

        val history = repository.listMessages(
            conversationId = "con-1",
            cursor = "message cursor",
            pageSize = 25,
        ).getOrThrow()
        assertEquals("older", history.nextCursor)
        assertEquals("thumb", history.items.single().videoPreview?.coverThumbnailUrl)
        server.takeRequest().also { request ->
            assertEquals(
                "/api/v1/chat/conversations/con-1/messages?cursor=message%20cursor&pageSize=25",
                request.path,
            )
        }

        val text = repository.sendText(
            conversationId = "con-1",
            text = "  保留服务端规范化前文本  ",
            clientMessageId = "client-text",
        ).getOrThrow()
        assertEquals(RemoteChatMessageType.TEXT, text.type)
        server.takeRequest().also { request ->
            val body = request.jsonBody()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-1/messages", request.path)
            assertEquals("text", body["type"].asString)
            assertEquals("  保留服务端规范化前文本  ", body["text"].asString)
            assertEquals("client-text", body["clientMessageId"].asString)
            assertFalse(body.has("contentId"))
        }

        val video = repository.sendVideoShare(
            conversationId = "con-1",
            contentId = "video-1",
            clientMessageId = "client-video",
        ).getOrThrow()
        assertEquals("video-1", video.sharedContentId)
        server.takeRequest().also { request ->
            val body = request.jsonBody()
            assertEquals("video_share", body["type"].asString)
            assertEquals("video-1", body["contentId"].asString)
            assertEquals("client-video", body["clientMessageId"].asString)
            assertFalse(body.has("text"))
        }
    }

    @Test
    fun httpFailuresRetainStatusForViewModelBranches() = runTest {
        val repository = repository()

        listOf(401, 403, 404, 409).forEach { statusCode ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(statusCode)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail":"chat-$statusCode"}"""),
            )

            val failure = repository.getConversation("con-$statusCode")
            val error = failure.exceptionOrNull()

            assertTrue(error is ChatApiException)
            error as ChatApiException
            assertEquals(statusCode, error.statusCode)
            assertEquals("chat-$statusCode", error.message)
        }
    }

    @Test
    fun markReadSendsMessageBodyOrNoBodyAndMapsNullableWatermark() = runTest {
        server.enqueue(
            json(
                """
                {
                  "conversationId":"con-1",
                  "lastReadMessageId":"msg-2",
                  "readAt":"2026-07-24T11:00:00Z",
                  "unreadCount":1
                }
                """,
            ),
        )
        server.enqueue(
            json(
                """
                {
                  "conversationId":"con-1",
                  "lastReadMessageId":null,
                  "readAt":"2026-07-24T11:01:00Z",
                  "unreadCount":0
                }
                """,
            ),
        )
        val repository = repository()

        val throughMessage = repository.markRead("con-1", "msg-2").getOrThrow()
        assertEquals("msg-2", throughMessage.lastReadMessageId)
        assertEquals(1L, throughMessage.unreadCount)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-1/read", request.path)
            assertEquals("msg-2", request.jsonBody()["messageId"].asString)
        }

        val throughLatest = repository.markRead("con-1").getOrThrow()
        assertNull(throughLatest.lastReadMessageId)
        assertEquals(0L, throughLatest.unreadCount)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/chat/conversations/con-1/read", request.path)
            assertEquals(0L, request.bodySize)
        }
    }

    private fun repository(): DefaultChatRepository {
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer access")
                        .build(),
                )
            }
            .build()
        return DefaultChatRepository(
            api = ChatApi(
                client = authenticatedClient,
                baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            ),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private fun groupDetail(): String =
        """
        {
          "id":"con-group",
          "type":"group",
          "name":"花友群",
          "ownerUserId":"owner",
          "state":"active",
          "dissolvedAt":null,
          "dissolvedByUserId":null,
          "members":[{
            "userId":"owner",
            "nickname":null,
            "avatarUrl":null,
            "role":"owner",
            "joinedAt":"2026-07-24T08:00:00Z"
          }],
          "createdAt":"2026-07-24T08:00:00Z",
          "updatedAt":"2026-07-24T08:00:00Z"
        }
        """

    private fun textMessage(): String =
        """
        {
          "id":"msg-text",
          "conversationId":"con-1",
          "senderUserId":"self",
          "type":"text",
          "text":"保留服务端规范化前文本",
          "sharedContentId":null,
          "clientMessageId":"client-text",
          "createdAt":"2026-07-24T10:01:00Z"
        }
        """

    private fun videoMessage(): String =
        """
        {
          "id":"msg-video-send",
          "conversationId":"con-1",
          "senderUserId":"self",
          "type":"video_share",
          "text":null,
          "sharedContentId":"video-1",
          "videoPreview":null,
          "clientMessageId":"client-video",
          "createdAt":"2026-07-24T10:02:00Z"
        }
        """

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())

    private fun RecordedRequest.jsonBody(): JsonObject =
        JsonParser.parseString(body.readUtf8()).asJsonObject
}
