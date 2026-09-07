package com.example.flower_show.data.repository

import com.example.flower_show.model.Result
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.VideoItem
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiVideoRepositoryTest {
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
    fun loadFeedParsesHlsAndKeepsMp4Fallback() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "type":"video",
                      "id":"v1",
                      "title":"HLS video",
                      "author":"Creator",
                      "authorUserId":"user-7",
                      "avatarUrl":"",
                      "videoUrl":"https://cdn.example/videos/v1/video.mp4",
                      "coverUrl":"",
                      "qualityUrls":{"360p":"https://cdn.example/videos/v1/video_360p.mp4"},
                      "hlsUrl":"https://cdn.example/videos/v1/hls/master.m3u8",
                      "likedByViewer":true,
                      "favoritedByViewer":true
                    }]
                    """.trimIndent(),
                ),
        )
        val repository = ApiVideoRepository(
            baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            client = OkHttpClient(),
        )

        val result = repository.loadFeed(1, 10) as Result.Success
        val video = result.data.single() as VideoItem

        assertEquals("https://cdn.example/videos/v1/hls/master.m3u8", video.hlsUrl)
        assertEquals("https://cdn.example/videos/v1/video.mp4", video.videoUrl)
        assertEquals("https://cdn.example/videos/v1/video_360p.mp4", video.qualityUrls?.get("360p"))
        assertEquals("user-7", video.authorUserId)
        assertTrue(video.likedByViewer)
        assertTrue(video.favoritedByViewer)
    }

    @Test
    fun missingViewerAndAuthorFieldsUseBackwardCompatibleDefaults() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"type":"video","id":"v1","title":"Legacy","author":"Creator","videoUrl":"v"}]""",
            ),
        )
        val repository = repository()

        val video = (repository.loadFeed(1, 10) as Result.Success)
            .data.single() as VideoItem

        assertNull(video.authorUserId)
        assertFalse(video.likedByViewer)
        assertFalse(video.favoritedByViewer)
    }

    @Test
    fun followingFeedUsesOpaqueCursorAndMapsViewerState() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "items":[{
                    "type":"video","id":"v2","title":"Following","author":"A",
                    "videoUrl":"v","likedByViewer":true
                  }],
                  "nextCursor":"opaque+/=",
                  "hasMore":true
                }
                """.trimIndent(),
            ),
        )
        val repository = repository()

        val result = repository.loadFollowingFeed("cursor+/=", 20) as Result.Success

        assertEquals("opaque+/=", result.data.nextCursor)
        assertTrue((result.data.items.single() as VideoItem).likedByViewer)
        val request = server.takeRequest()
        assertEquals("/api/v1/feed/following?cursor=cursor%2B%2F%3D&pageSize=20", request.path)
    }

    @Test
    fun unlikeAndUnfavoriteUseDeleteWithoutRequestBody() {
        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"changed":true,"stats":{
                      "contentId":"v1","likeCount":1,"commentCount":2,
                      "favoriteCount":3,"shareCount":4,"viewCount":5
                    }}
                    """.trimIndent(),
                ),
            )
        }
        val repository = repository()

        repository.setLiked("v1", "ignored-on-delete", liked = false)
        repository.setFavorited("v1", "ignored-on-delete", favorited = false)

        val unlike = server.takeRequest()
        val unfavorite = server.takeRequest()
        assertEquals("DELETE", unlike.method)
        assertEquals("/api/v1/contents/v1/like", unlike.path)
        assertEquals(0L, unlike.bodySize)
        assertEquals("DELETE", unfavorite.method)
        assertEquals("/api/v1/contents/v1/favorite", unfavorite.path)
        assertEquals(0L, unfavorite.bodySize)
    }

    @Test
    fun likePostKeepsCurrentServerUserIdBodyContract() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"changed":true,"stats":{
                  "contentId":"v1","likeCount":1,"commentCount":0,
                  "favoriteCount":0,"shareCount":0,"viewCount":0
                }}
                """.trimIndent(),
            ),
        )
        val repository = repository()

        repository.setLiked("v1", "jwt-user", liked = true)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"userId":"jwt-user"}""", request.body.readUtf8())
    }

    @Test
    fun commentsAreLoadedAndCreatedThroughBackend() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "id":"c1","contentId":"v1","userId":"u2","nickname":"朋友",
                  "avatarUrl":"avatar","body":"真好看","likeCount":2,"createdAt":"now"
                }]
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id":"c2","contentId":"v1","userId":"u1","nickname":"我",
                  "avatarUrl":"","body":"谢谢","likeCount":0,"createdAt":"now"
                }
                """.trimIndent(),
            ),
        )
        val repository = repository()

        val loaded = repository.loadComments("v1") as Result.Success
        val created = repository.createComment("v1", "u1", "谢谢") as Result.Success

        assertEquals("真好看", loaded.data.single().body)
        assertEquals("c2", created.data.id)
        assertEquals("GET", server.takeRequest().method)
        val createRequest = server.takeRequest()
        assertEquals("POST", createRequest.method)
        assertEquals("/api/v1/contents/v1/comments", createRequest.path)
        assertEquals("""{"userId":"u1","body":"谢谢"}""", createRequest.body.readUtf8())
    }

    @Test
    fun recommendedFeedPostsEnvelopeCursorAndStableInstallHeader() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(
                    """
                    {
                      "serveSessionId":"serve-2",
                      "items":[{
                        "id":"delivery-9","rank":7,"source":"personalized",
                        "deliveryType":"organic","exposureToken":"exposure-9",
                        "relatedSearch":"server ranked search",
                        "payload":{
                          "type":"video","id":"v9","title":"Ranked","author":"A",
                          "videoUrl":"video","coverThumbnailUrl":"thumb",
                          "contentSearches":["server-only"]
                        }
                      }],
                      "nextCursor":"opaque+/=","hasMore":true,
                      "serveMode":"personalized","algoVersion":"a1",
                      "policyVersion":"p1","expiresAtMs":999999
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = repository(installId = "stable-install")

        val result = repository.loadRecommendedFeed(
            cursor = "cursor+/=",
            serveSessionId = "serve-1",
            pageSize = 10,
            refresh = false,
        ) as Result.Success

        assertEquals("serve-2", result.data.serveSessionId)
        assertEquals("opaque+/=", result.data.nextCursor)
        assertEquals("delivery-9", result.data.items.single().delivery.id)
        assertEquals("exposure-9", result.data.items.single().delivery.exposureToken)
        assertEquals("server ranked search", result.data.items.single().delivery.relatedSearch)
        val video = result.data.items.single().card as VideoItem
        assertEquals("thumb", video.coverThumbnailUrl)
        assertEquals(listOf("server-only"), video.contentSearches)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/feed/pages", request.path)
        assertEquals("stable-install", request.getHeader("X-Install-Id"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertTrue(body["clientRequestId"].asString.isNotBlank())
        assertEquals("serve-1", body["serveSessionId"].asString)
        assertEquals("cursor+/=", body["cursor"].asString)
        assertEquals(10, body["limit"].asInt)
        assertFalse(body["refresh"].asBoolean)
    }

    @Test
    fun recommendedFeedRefreshStartsFreshRequestsWithNewClientRequestIds() {
        repeat(2) { index ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    envelope(
                        """
                        {
                          "serveSessionId":"refresh-session-$index",
                          "items":[],
                          "nextCursor":null,
                          "hasMore":false,
                          "serveMode":"default",
                          "algoVersion":"a1",
                          "policyVersion":"p1",
                          "expiresAtMs":999999
                        }
                        """.trimIndent(),
                    ),
                ),
            )
        }
        val repository = repository()

        repeat(2) {
            assertTrue(
                repository.loadRecommendedFeed(
                    cursor = null,
                    serveSessionId = null,
                    pageSize = 10,
                    refresh = true,
                ) is Result.Success,
            )
        }

        val bodies = List(2) {
            JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        }
        bodies.forEach { body ->
            assertTrue(body["refresh"].asBoolean)
            assertFalse(body.has("cursor"))
            assertFalse(body.has("serveSessionId"))
            assertTrue(body["clientRequestId"].asString.isNotBlank())
        }
        assertNotEquals(
            bodies[0]["clientRequestId"].asString,
            bodies[1]["clientRequestId"].asString,
        )
    }

    @Test
    fun searchGuessesUseServerPagesWithoutLocalRotation() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(guessData("guess-session", "guess-cursor", hasMore = true, text = "first")),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(guessData("guess-session", null, hasMore = false, text = "second")),
            ),
        )
        val repository = repository()

        val first = repository.loadSearchGuesses(null, null, 8, refresh = true) as Result.Success
        val second = repository.loadSearchGuesses(
            first.data.nextCursor,
            first.data.serveSessionId,
            8,
            refresh = false,
        ) as Result.Success

        assertEquals(listOf("first"), first.data.items.map { it.text })
        assertEquals(listOf("second"), second.data.items.map { it.text })
        val firstBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val secondRequest = server.takeRequest()
        val secondBody = JsonParser.parseString(secondRequest.body.readUtf8()).asJsonObject
        assertEquals("/api/v1/search/guesses", secondRequest.path)
        assertTrue(firstBody["refresh"].asBoolean)
        assertEquals("guess-session", secondBody["serveSessionId"].asString)
        assertEquals("guess-cursor", secondBody["cursor"].asString)
        assertFalse(secondBody["refresh"].asBoolean)
    }

    @Test
    fun searchVideosPostsQueryAndOpaqueCursor() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(
                    """
                    {
                      "serveSessionId":"search-session",
                      "items":[{
                        "id":"search-delivery","rank":0,"source":"search",
                        "deliveryType":"organic","exposureToken":"search-exposure",
                        "payload":{"type":"video","id":"v1","title":"Rose","author":"A","videoUrl":"v"}
                      }],
                      "nextCursor":null,"hasMore":false,
                      "algoVersion":"search-a","policyVersion":"search-p","expiresAtMs":88
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = repository()

        val result = repository.searchVideos("  rose  ", "opaque+/=", 20) as Result.Success

        assertEquals("v1", (result.data.items.single().card as VideoItem).id)
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("POST", request.method)
        assertEquals("/api/v1/search/videos", request.path)
        assertEquals("rose", body["query"].asString)
        assertEquals("opaque+/=", body["cursor"].asString)
        assertEquals(20, body["limit"].asInt)
    }

    @Test
    fun eventBatchPostsExactDeliveryFields() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(
                    """{"results":[{"eventId":"event-1","status":"accepted"}]}""",
                ),
            ),
        )
        val repository = repository(installId = "events-install")
        val event = RecommendationEvent(
            eventId = "event-1",
            type = "impression",
            occurredAtMs = 1234,
            exposureToken = "exposure",
            contentId = "v1",
            sequence = 7,
        )

        val result = repository.reportEvents(listOf(event)) as Result.Success

        assertEquals("accepted", result.data.single().status)
        val request = server.takeRequest()
        assertEquals("/api/v1/events:batch", request.path)
        assertEquals("events-install", request.getHeader("X-Install-Id"))
        val sent = JsonParser.parseString(request.body.readUtf8())
            .asJsonObject["events"].asJsonArray.single().asJsonObject
        assertEquals("event-1", sent["eventId"].asString)
        assertEquals("impression", sent["type"].asString)
        assertEquals("exposure", sent["exposureToken"].asString)
        assertEquals("v1", sent["contentId"].asString)
        assertEquals(7L, sent["sequence"].asLong)
    }

    @Test
    fun missingEnvelopeFieldIsAnErrorAndNeverFallsBackLocally() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "requestId":"r","traceId":"t","serverTimeMs":1,
                  "data":{
                    "serveSessionId":"s","items":[],"nextCursor":null,"hasMore":false,
                    "serveMode":"default","algoVersion":"a","policyVersion":"p","expiresAtMs":2
                  }
                }
                """.trimIndent(),
            ),
        )
        val repository = repository()

        val result = repository.loadRecommendedFeed(null, null, 10, refresh = true)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("missing error"))
    }

    @Test
    fun malformedRankedPayloadIsAnErrorInsteadOfDroppingTheItem() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(
                    """
                    {
                      "serveSessionId":"s",
                      "items":[{
                        "id":"d","rank":0,"source":"server","deliveryType":"organic",
                        "exposureToken":"e","payload":{"type":"video","title":"missing id"}
                      }],
                      "nextCursor":null,"hasMore":false,"serveMode":"default",
                      "algoVersion":"a","policyVersion":"p","expiresAtMs":2
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = repository()

        val result = repository.loadRecommendedFeed(null, null, 10, refresh = true)

        assertTrue(result is Result.Error)
    }

    @Test
    fun nonOrganicRankedItemIsFilteredOutWithoutFailingThePage() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                envelope(
                    """
                    {
                      "serveSessionId":"s",
                      "items":[{
                        "id":"d","rank":1,"source":"server","deliveryType":"ad",
                        "exposureToken":"e",
                        "payload":{"type":"video","id":"v1","title":"Sponsored","author":"A","videoUrl":"v"}
                      }],
                      "nextCursor":null,"hasMore":false,"serveMode":"default",
                      "algoVersion":"a","policyVersion":"organic-only-v1","expiresAtMs":2
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = repository()

        val result = repository.loadRecommendedFeed(null, null, 10, refresh = true)

        // 单条广告位不应毒化整页：过滤掉非 organic 项后页面仍成功返回。
        assertTrue(result is Result.Success)
        val page = (result as Result.Success).data
        assertTrue(page.items.isEmpty())
    }

    private fun repository(installId: String = "test-install"): ApiVideoRepository = ApiVideoRepository(
        baseUrl = server.url("/api/v1").toString().trimEnd('/'),
        client = OkHttpClient(),
        installId = installId,
    )

    private fun envelope(data: String): String =
        """
        {
          "requestId":"request-1",
          "traceId":"trace-1",
          "serverTimeMs":123,
          "data":$data,
          "error":null
        }
        """.trimIndent()

    private fun guessData(
        sessionId: String,
        cursor: String?,
        hasMore: Boolean,
        text: String,
    ): String =
        """
        {
          "serveSessionId":"$sessionId",
          "items":[{
            "id":"suggestion-$text","text":"$text","rank":0,
            "source":"server","exposureToken":"exposure-$text"
          }],
          "nextCursor":${cursor?.let { "\"$it\"" } ?: "null"},
          "hasMore":$hasMore,
          "algoVersion":"guess-a","policyVersion":"guess-p","expiresAtMs":77
        }
        """.trimIndent()
}
