package com.example.flower_show.data.auth

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultAuthRepositoryTest {
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
    fun logoutClearsLocalSessionBeforeNetworkAndSendsCapturedTokenPair() = runBlocking {
        val requestArrived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        server.dispatcher = blockingSuccessDispatcher(requestArrived, releaseResponse, "{\"revoked\":true}")
        val fixture = repositoryFixture()

        val logout = async(Dispatchers.Default) { fixture.repository.logout() }
        assertTrue("logout request did not arrive", requestArrived.await(2, TimeUnit.SECONDS))

        fixture.assertLocalSessionCleared()
        releaseResponse.countDown()
        assertTrue(withTimeout(5_000) { logout.await() }.isSuccess)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/logout", request.path)
        assertEquals("Bearer expired-access", request.getHeader("Authorization"))
        assertEquals("expired-refresh", request.jsonBody()["refreshToken"])
        assertEquals(1, fixture.store.clearCount)
    }

    @Test
    fun logoutAllClearsLocalSessionBeforeNetworkAndUsesCapturedAccessToken() = runBlocking {
        val requestArrived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        server.dispatcher = blockingSuccessDispatcher(
            requestArrived,
            releaseResponse,
            "{\"revokedCount\":3}",
        )
        val fixture = repositoryFixture()

        val logout = async(Dispatchers.Default) { fixture.repository.logoutAll() }
        assertTrue("logout-all request did not arrive", requestArrived.await(2, TimeUnit.SECONDS))

        fixture.assertLocalSessionCleared()
        releaseResponse.countDown()
        assertTrue(withTimeout(5_000) { logout.await() }.isSuccess)

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/logout-all", request.path)
        assertEquals("Bearer expired-access", request.getHeader("Authorization"))
        assertTrue(request.jsonBody().isEmpty())
        assertEquals(1, fixture.store.clearCount)
    }

    @Test
    fun expiredAccessLogoutRefreshesCapturedTokenThenRevokesRotatedPairWithoutRestoringIt() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"access token expired\"}"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(repositoryTokenResponseJson("rotated-access", "rotated-refresh")),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"revoked\":true}"),
        )
        val fixture = repositoryFixture()

        val result = fixture.repository.logout()

        assertTrue(result.isSuccess)
        fixture.assertLocalSessionCleared()
        assertFalse(fixture.store.writes.contains("rotated-refresh"))

        val expiredLogout = takeRequest()
        assertEquals("/api/v1/auth/logout", expiredLogout.path)
        assertEquals("Bearer expired-access", expiredLogout.getHeader("Authorization"))
        assertEquals("expired-refresh", expiredLogout.jsonBody()["refreshToken"])

        val refresh = takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("expired-refresh", refresh.jsonBody()["refreshToken"])

        val rotatedLogout = takeRequest()
        assertEquals("/api/v1/auth/logout", rotatedLogout.path)
        assertEquals("Bearer rotated-access", rotatedLogout.getHeader("Authorization"))
        assertEquals("rotated-refresh", rotatedLogout.jsonBody()["refreshToken"])
        assertEquals(3, server.requestCount)
    }

    private fun repositoryFixture(): RepositoryFixture {
        val store = RecordingRefreshTokenStore()
        val sessionManager = AuthSessionManager(store)
        sessionManager.replace(repositoryAuthTokenResponse("expired-access", "expired-refresh"))
        val api = AuthApi(
            client = OkHttpClient(),
            authBaseUrl = server.url("/api/v1/auth").toString(),
        )
        return RepositoryFixture(
            repository = DefaultAuthRepository(
                authApi = api,
                sessionManager = sessionManager,
                refreshCoordinator = TokenRefreshCoordinator(api, sessionManager),
                ioDispatcher = Dispatchers.IO,
            ),
            sessionManager = sessionManager,
            store = store,
        )
    }

    private fun blockingSuccessDispatcher(
        requestArrived: CountDownLatch,
        releaseResponse: CountDownLatch,
        body: String,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requestArrived.countDown()
            check(releaseResponse.await(5, TimeUnit.SECONDS)) { "Test did not release server response" }
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        }
    }

    private fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected an authentication request" }

    private fun RecordedRequest.jsonBody(): Map<String, String?> {
        val json = JsonParser.parseString(body.readUtf8()).asJsonObject
        return json.entrySet().associate { (key, value) ->
            key to if (value.isJsonNull) null else value.asString
        }
    }
}

private data class RepositoryFixture(
    val repository: DefaultAuthRepository,
    val sessionManager: AuthSessionManager,
    val store: RecordingRefreshTokenStore,
) {
    fun assertLocalSessionCleared() {
        assertNull(sessionManager.currentSession())
        assertNull(sessionManager.currentAccessToken())
        assertNull(sessionManager.currentRefreshToken())
        assertNull(sessionManager.currentUser.value)
        assertNull(store.token)
    }
}

private class RecordingRefreshTokenStore : RefreshTokenStore {
    var token: String? = null
    var clearCount: Int = 0
    val writes = mutableListOf<String>()

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        writes += refreshToken
        token = refreshToken
    }

    override fun clear() {
        clearCount++
        token = null
    }
}

private fun repositoryTokenResponseJson(accessToken: String, refreshToken: String): String =
    """
    {
      "tokenType": "Bearer",
      "accessToken": "$accessToken",
      "accessTokenExpiresInSeconds": 900,
      "refreshToken": "$refreshToken",
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
    """.trimIndent()

private fun repositoryAuthTokenResponse(
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
