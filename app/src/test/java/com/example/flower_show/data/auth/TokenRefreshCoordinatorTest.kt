package com.example.flower_show.data.auth

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TokenRefreshCoordinatorTest {
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
    fun concurrentUnauthorizedRequestsUsingSameOldAccessRefreshExactlyOnce() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = successfulRefreshResponse()
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
        }
        val store = CoordinatorRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(coordinatorTokenResponse("old-access", "old-refresh"))
        val coordinator = TokenRefreshCoordinator(authApi(), manager)
        val workerCount = 12
        val executor = Executors.newFixedThreadPool(workerCount)
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)

        try {
            val results = (1..workerCount).map {
                executor.submit<String?> {
                    ready.countDown()
                    start.await()
                    coordinator.refreshAfterUnauthorized("old-access")
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()

            results.forEach { future ->
                assertEquals("new-access", future.get(5, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, server.requestCount)
        assertEquals("new-access", manager.currentAccessToken())
        assertEquals("new-refresh", manager.currentRefreshToken())
        assertEquals("new-refresh", store.token)
        assertEquals("server-user-id", manager.currentUserId())

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/refresh", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("old-refresh", body.get("refreshToken").asString)
    }

    @Test
    fun concurrentTransientRefreshFailuresAreCoalescedAndKeepExistingSession() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"temporary authentication outage"}""")
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
        }
        val store = CoordinatorRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(coordinatorTokenResponse("old-access", "old-refresh"))
        val coordinator = TokenRefreshCoordinator(
            authApi = authApi(),
            sessionManager = manager,
            nanoTime = { 1_000_000_000L },
        )
        val workerCount = 12
        val executor = Executors.newFixedThreadPool(workerCount)
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)

        try {
            val results = (1..workerCount).map {
                executor.submit<String?> {
                    ready.countDown()
                    start.await()
                    coordinator.refreshAfterUnauthorized("old-access")
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()

            results.forEach { future ->
                assertNull(future.get(5, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, server.requestCount)
        assertEquals("old-access", manager.currentAccessToken())
        assertEquals("old-refresh", manager.currentRefreshToken())
        assertEquals("server-user-id", manager.currentUserId())
        assertEquals("old-refresh", store.token)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun refresh401ClearsAccessRefreshAndCurrentUser() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"refresh token expired"}"""),
        )
        val store = CoordinatorRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(coordinatorTokenResponse("expired-access", "expired-refresh"))
        val coordinator = TokenRefreshCoordinator(authApi(), manager)

        val refreshed = coordinator.refreshAfterUnauthorized("expired-access")

        assertNull(refreshed)
        assertNull(manager.currentAccessToken())
        assertNull(manager.currentRefreshToken())
        assertNull(manager.currentUser.value)
        assertNull(store.token)
        assertEquals(1, store.clearCount)
    }

    private fun authApi(): AuthApi = AuthApi(
        client = OkHttpClient(),
        authBaseUrl = server.url("/api/v1/auth").toString(),
    )

    private fun successfulRefreshResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "tokenType": "Bearer",
              "accessToken": "new-access",
              "accessTokenExpiresInSeconds": 900,
              "refreshToken": "new-refresh",
              "refreshTokenExpiresInSeconds": 2592000,
              "user": {
                "userId": "server-user-id",
                "accountId": "account-id",
                "username": "garden.user",
                "nickname": "花园用户",
                "avatarUrl": null,
                "role": "USER"
              }
            }
            """.trimIndent(),
        )
}

private class CoordinatorRefreshTokenStore : RefreshTokenStore {
    var token: String? = null
    var clearCount: Int = 0

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        clearCount++
        token = null
    }
}

private fun coordinatorTokenResponse(
    accessToken: String,
    refreshToken: String,
): AuthTokenResponse = AuthTokenResponse(
    tokenType = "Bearer",
    accessToken = accessToken,
    accessTokenExpiresInSeconds = 900,
    refreshToken = refreshToken,
    refreshTokenExpiresInSeconds = 2_592_000,
    user = AuthUser(
        userId = "server-user-id",
        accountId = "account-id",
        username = "garden.user",
        nickname = "花园用户",
        role = "USER",
    ),
)
