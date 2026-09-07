package com.example.flower_show.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.util.concurrent.TimeUnit

class AuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = AuthApi(
            client = OkHttpClient(),
            authBaseUrl = server.url("/api/v1/auth/").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun registerUsesExpectedEndpointAndJsonAndAccepts201TokenResponse() {
        server.enqueue(tokenResponse(statusCode = 201, avatarUrl = "https://example.com/avatar.jpg"))

        val response = api.register(
            RegisterRequest(
                username = "garden.user",
                password = "Garden@123",
                nickname = "花园用户",
                avatarUrl = "https://example.com/avatar.jpg",
                bio = "阳台种花",
            ),
        )

        assertTokenResponse(response, expectedAvatarUrl = "https://example.com/avatar.jpg")
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/register", request.path)
        assertNull(request.getHeader("Authorization"))
        assertJsonContentType(request)
        assertEquals(
            mapOf(
                "username" to "garden.user",
                "password" to "Garden@123",
                "nickname" to "花园用户",
                "avatarUrl" to "https://example.com/avatar.jpg",
                "bio" to "阳台种花",
            ),
            request.stringBody(),
        )
    }

    @Test
    fun loginUsesExpectedEndpointAndJsonAndMapsServerUserIdAndExpiry() {
        server.enqueue(tokenResponse(statusCode = 200, avatarUrl = null))

        val response = api.login(LoginRequest(username = "garden.user", password = "Garden@123"))

        assertTokenResponse(response, expectedAvatarUrl = null)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertJsonContentType(request)
        assertEquals(
            mapOf(
                "username" to "garden.user",
                "password" to "Garden@123",
            ),
            request.stringBody(),
        )
    }

    @Test
    fun refreshUsesExpectedEndpointAndSingleRefreshTokenField() {
        server.enqueue(
            tokenResponse(
                statusCode = 200,
                accessToken = "rotated-access",
                refreshToken = "rotated-refresh",
                avatarUrl = "https://example.com/rotated.jpg",
            ),
        )

        val response = api.refresh("current-refresh-token")

        assertEquals("rotated-access", response.accessToken)
        assertEquals("rotated-refresh", response.refreshToken)
        assertEquals("server-user-id", response.user.userId)
        assertEquals("https://example.com/rotated.jpg", response.user.avatarUrl)
        assertEquals(900L, response.accessTokenExpiresInSeconds)
        assertEquals(2_592_000L, response.refreshTokenExpiresInSeconds)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/refresh", request.path)
        assertNull(request.getHeader("Authorization"))
        assertJsonContentType(request)
        assertEquals(mapOf("refreshToken" to "current-refresh-token"), request.stringBody())
    }

    @Test
    fun authenticationModelsRedactPasswordsAndTokensFromToString() {
        val register = RegisterRequest("garden.user", "raw-password", "花园用户")
        val login = LoginRequest("garden.user", "raw-password")
        val refresh = RefreshRequest("raw-refresh-token")
        val tokenResponse = authTokenResponse("raw-access-token", "raw-refresh-token")

        assertFalse(register.toString().contains("raw-password"))
        assertFalse(login.toString().contains("raw-password"))
        assertFalse(refresh.toString().contains("raw-refresh-token"))
        assertFalse(tokenResponse.toString().contains("raw-access-token"))
        assertFalse(tokenResponse.toString().contains("raw-refresh-token"))
        assertTrue(register.toString().contains("password=<redacted>"))
        assertTrue(tokenResponse.toString().contains("accessToken=<redacted>"))
        assertTrue(tokenResponse.toString().contains("refreshToken=<redacted>"))
    }

    private fun assertTokenResponse(response: AuthTokenResponse, expectedAvatarUrl: String?) {
        assertEquals("Bearer", response.tokenType)
        assertEquals("access-token", response.accessToken)
        assertEquals(900L, response.accessTokenExpiresInSeconds)
        assertEquals("refresh-token", response.refreshToken)
        assertEquals(2_592_000L, response.refreshTokenExpiresInSeconds)
        assertEquals("server-user-id", response.user.userId)
        assertEquals("account-id", response.user.accountId)
        assertEquals("garden.user", response.user.username)
        assertEquals("花园用户", response.user.nickname)
        assertEquals(expectedAvatarUrl, response.user.avatarUrl)
        assertEquals("USER", response.user.role)
    }

    private fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected an authentication request" }

    private fun assertJsonContentType(request: RecordedRequest) {
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertTrue(request.getHeader("Accept").orEmpty().startsWith("application/json"))
    }

    private fun RecordedRequest.stringBody(): Map<String, String?> {
        val json = JsonParser.parseString(body.readUtf8()).asJsonObject
        return json.entrySet().associate { (key, value) ->
            key to if (value.isJsonNull) null else value.asString
        }
    }

    private fun tokenResponse(
        statusCode: Int,
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        avatarUrl: String?,
    ): MockResponse = MockResponse()
        .setResponseCode(statusCode)
        .setHeader("Content-Type", "application/json")
        .setBody(tokenResponseJson(accessToken, refreshToken, avatarUrl))
}

private fun tokenResponseJson(
    accessToken: String,
    refreshToken: String,
    avatarUrl: String?,
): String {
    val user = JsonObject().apply {
        addProperty("userId", "server-user-id")
        addProperty("accountId", "account-id")
        addProperty("username", "garden.user")
        addProperty("nickname", "花园用户")
        if (avatarUrl == null) add("avatarUrl", null) else addProperty("avatarUrl", avatarUrl)
        addProperty("role", "USER")
    }
    return JsonObject().apply {
        addProperty("tokenType", "Bearer")
        addProperty("accessToken", accessToken)
        addProperty("accessTokenExpiresInSeconds", 900)
        addProperty("refreshToken", refreshToken)
        addProperty("refreshTokenExpiresInSeconds", 2_592_000)
        add("user", user)
    }.toString()
}

private fun authTokenResponse(accessToken: String, refreshToken: String): AuthTokenResponse =
    AuthTokenResponse(
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
