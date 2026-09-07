package com.example.flower_show.data.auth

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class TokenAuthenticatorTest {
    private lateinit var trustedServer: MockWebServer
    private lateinit var untrustedServer: MockWebServer

    @Before
    fun setUp() {
        trustedServer = MockWebServer().also { it.start() }
        untrustedServer = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        trustedServer.shutdown()
        untrustedServer.shutdown()
    }

    @Test
    fun trustedOriginRefreshesAndRetriesWithNewBearerWithoutLegacyUserId() {
        trustedServer.enqueue(successfulAuthenticatorRefreshResponse())
        val manager = authenticatorSessionManager()
        val authenticator = TokenAuthenticator(
            refreshCoordinator = authenticatorCoordinator(manager),
            trustedOrigin = trustedServer.url("/"),
        )
        val failedRequest = Request.Builder()
            .url(trustedServer.url("/protected"))
            .header("Authorization", "Bearer old-access")
            .header("X-User-Id", "legacy-user-id")
            .build()

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorized(failedRequest),
        )

        requireNotNull(retriedRequest)
        assertEquals("Bearer new-access", retriedRequest.header("Authorization"))
        assertNull(retriedRequest.header("X-User-Id"))
        assertEquals("new-access", manager.currentAccessToken())
        assertEquals("new-refresh", manager.currentRefreshToken())
        val refreshRequest = trustedServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/refresh", refreshRequest.path)
    }

    @Test
    fun untrustedOriginDoesNotAttemptRefresh() {
        val manager = authenticatorSessionManager()
        val authenticator = TokenAuthenticator(
            refreshCoordinator = authenticatorCoordinator(manager),
            trustedOrigin = trustedServer.url("/"),
        )
        val failedRequest = Request.Builder()
            .url(untrustedServer.url("/external"))
            .header("Authorization", "Bearer old-access")
            .build()

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorized(failedRequest),
        )

        assertNull(retriedRequest)
        assertEquals(0, trustedServer.requestCount)
        assertEquals("old-access", manager.currentAccessToken())
        assertEquals("old-refresh", manager.currentRefreshToken())
    }

    @Test
    fun secondUnauthorizedResponseIsNotRetriedAgain() {
        val manager = authenticatorSessionManager()
        val authenticator = TokenAuthenticator(
            refreshCoordinator = authenticatorCoordinator(manager),
            trustedOrigin = trustedServer.url("/"),
        )
        val request = Request.Builder()
            .url(trustedServer.url("/protected"))
            .header("Authorization", "Bearer old-access")
            .build()
        val firstUnauthorized = unauthorized(request)
        val secondUnauthorized = unauthorized(request, priorResponse = firstUnauthorized)

        val thirdAttempt = authenticator.authenticate(null, secondUnauthorized)

        assertNull(thirdAttempt)
        assertEquals(0, trustedServer.requestCount)
        assertEquals("old-access", manager.currentAccessToken())
    }

    private fun authenticatorCoordinator(manager: AuthSessionManager): TokenRefreshCoordinator =
        TokenRefreshCoordinator(
            authApi = AuthApi(
                client = OkHttpClient(),
                authBaseUrl = trustedServer.url("/api/v1/auth").toString(),
            ),
            sessionManager = manager,
        )

    private fun unauthorized(
        request: Request,
        priorResponse: Response? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .apply { if (priorResponse != null) priorResponse(priorResponse) }
        .build()

    private fun successfulAuthenticatorRefreshResponse(): MockResponse = MockResponse()
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

private class AuthenticatorRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}

private fun authenticatorSessionManager(): AuthSessionManager = AuthSessionManager(
    AuthenticatorRefreshTokenStore(),
).also { manager ->
    manager.replace(
        AuthTokenResponse(
            tokenType = "Bearer",
            accessToken = "old-access",
            accessTokenExpiresInSeconds = 900,
            refreshToken = "old-refresh",
            refreshTokenExpiresInSeconds = 2_592_000,
            user = AuthUser(
                userId = "server-user-id",
                accountId = "account-id",
                username = "garden.user",
                nickname = "花园用户",
                role = "USER",
            ),
        ),
    )
}
