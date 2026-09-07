package com.example.flower_show.data.repository

import com.example.flower_show.data.remote.SocialApi
import com.example.flower_show.model.Result
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultSocialRepositoryTest {
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
    fun profileContentRelationsAndFollowMapBackendContracts() {
        server.enqueue(
            json(
                """
                {
                  "id":"u1","handle":"garden","nickname":"花园",
                  "bio":"bio","location":"杭州","postCount":2,
                  "receivedLikeCount":9,"followingCount":3,"followerCount":4
                }
                """,
            ),
        )
        server.enqueue(
            json(
                """
                {"items":[{
                  "type":"video","id":"v1","title":"作品","author":"花园",
                  "authorUserId":"u1","videoUrl":"video","coverUrl":"cover"
                }],"page":1,"pageSize":50,"total":1,"hasMore":false}
                """,
            ),
        )
        server.enqueue(
            json(
                """
                {"items":[{
                  "id":"u2","handle":"friend","nickname":"朋友","bio":"",
                  "location":"上海","followerCount":7,"followingCount":8,
                  "following":false,"followedBy":true,"mutualFollow":false
                }],"page":1,"pageSize":50,"total":1,"hasMore":false}
                """,
            ),
        )
        server.enqueue(
            json(
                """
                {
                  "changed":true,"targetUserId":"u2","following":true,
                  "followedBy":true,"mutualFollow":true,
                  "followerCount":8,"followingCount":4
                }
                """,
            ),
        )
        val repository = repository()

        val profile = (repository.loadProfile("u1") as Result.Success).data
        val videos = (
            repository.loadProfileVideos("u1", "posts") as Result.Success
            ).data.items
        val relations = (
            repository.loadRelations("u1", "followers", "朋") as Result.Success
            ).data.items
        val follow = (
            repository.setFollowing("u1", "u2", following = true) as Result.Success
            ).data

        assertEquals("garden", profile.account)
        assertEquals("u1", videos.single().authorUserId)
        assertTrue(relations.single().followsMe)
        assertTrue(follow.mutualFollow)
        assertEquals(4, follow.actorFollowingCount)

        repeat(4) {
            assertEquals("Bearer access", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun notificationActorFieldsUnreadAndReadEndpointsMap() {
        server.enqueue(
            json(
                """
                {
                  "items":[{
                    "id":"n1","actorUserId":"u2","actorNickname":"朋友",
                    "actorAvatarUrl":"avatar","type":"comment","contentId":"v1",
                    "message":"评论了你","read":false,"createdAt":"now"
                  }],
                  "nextCursor":"next","hasMore":true
                }
                """,
            ),
        )
        server.enqueue(json("""{"unreadCount":3}"""))
        server.enqueue(json("""{"changed":true,"unreadCount":2}"""))
        server.enqueue(json("""{"changedCount":2,"unreadCount":0}"""))
        val repository = repository()

        val page = (repository.loadNotifications() as Result.Success).data
        val unread = (repository.loadNotificationUnreadCount() as Result.Success).data
        val afterOne = (repository.markNotificationRead("n1") as Result.Success).data
        val afterAll = (repository.markAllNotificationsRead() as Result.Success).data

        assertEquals("朋友", page.items.single().actorNickname)
        assertEquals("avatar", page.items.single().actorAvatarUrl)
        assertEquals("next", page.nextCursor)
        assertEquals(3, unread)
        assertEquals(2, afterOne)
        assertEquals(0, afterAll)
        assertEquals("/api/v1/me/notifications/n1/read", server.takeRequestAt(2).path)
    }

    @Test
    fun unfollowUsesDeleteAndProfileVisibilityUsesPatch() {
        server.enqueue(
            json(
                """
                {
                  "changed":true,"targetUserId":"u2","following":false,
                  "followedBy":true,"mutualFollow":false,
                  "followerCount":7,"followingCount":2
                }
                """,
            ),
        )
        server.enqueue(
            json(
                """{"contentId":"v1","showOnProfile":false,"pinned":false,"sortOrder":0}""",
            ),
        )
        val repository = repository()

        repository.setFollowing("u1", "u2", following = false)
        repository.setWorkVisible("u1", "v1", visible = false)

        val unfollow = server.takeRequest()
        assertEquals("DELETE", unfollow.method)
        assertEquals("/api/v1/users/u1/following/u2", unfollow.path)
        assertEquals(0L, unfollow.bodySize)
        val visibility = server.takeRequest()
        assertEquals("PATCH", visibility.method)
        assertEquals(
            """{"showOnProfile":false}""",
            visibility.body.readUtf8(),
        )
    }

    private fun repository(): DefaultSocialRepository {
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer access")
                        .build(),
                )
            }
            .build()
        return DefaultSocialRepository(
            SocialApi(
                client = authenticatedClient,
                baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            ),
        )
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())

    private fun MockWebServer.takeRequestAt(index: Int) =
        List(index + 1) { takeRequest() }.last()
}
