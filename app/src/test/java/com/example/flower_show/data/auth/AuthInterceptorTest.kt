package com.example.flower_show.data.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthInterceptorTest {
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
    fun trustedOriginGetsBearerAndNeverGetsLegacyUserIdHeader() {
        val manager = coreSessionManagerWithTokens()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(manager, trustedServer.url("/")))
            .build()
        trustedServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder()
                .url(trustedServer.url("/protected"))
                .header("Authorization", "Bearer stale-access")
                .header("X-User-Id", "spoofed-user")
                .build(),
        ).execute().close()

        val recorded = trustedServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer session-access", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("X-User-Id"))
    }

    @Test
    fun sessionBearerIsNotLeakedToDifferentOrigin() {
        val manager = coreSessionManagerWithTokens()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(manager, trustedServer.url("/")))
            .build()
        untrustedServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder()
                .url(untrustedServer.url("/external-image"))
                .header("X-User-Id", "legacy-user")
                .build(),
        ).execute().close()

        val recorded = untrustedServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("X-User-Id"))
    }

    @Test
    fun webSocketUrlStringGetsBearerOnTrustedOrigin() {
        val manager = coreSessionManagerWithTokens()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(manager, trustedServer.url("/")))
            .build()
        trustedServer.enqueue(MockResponse().setResponseCode(200))

        // OkHttp 的 Request.Builder.url(String) 会把 ws:// 静默转换为 http://，
        // 拦截器看到的是转换后的 URL。这里走聊天握手同款路径，验证 Authorization 被添加。
        val wsUrlString = "ws://${trustedServer.hostName}:${trustedServer.port}/ws/chat"
        client.newCall(Request.Builder().url(wsUrlString).build()).execute().close()

        val recorded = trustedServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer session-access", recorded.getHeader("Authorization"))
    }

    @Test
    fun webSocketUrlStringOnDifferentHostDoesNotGetBearer() {
        val manager = coreSessionManagerWithTokens()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(manager, trustedServer.url("/")))
            .build()
        untrustedServer.enqueue(MockResponse().setResponseCode(200))

        val untrustedWs = "ws://${untrustedServer.hostName}:${untrustedServer.port}/ws/chat"
        client.newCall(Request.Builder().url(untrustedWs).build()).execute().close()

        val recorded = untrustedServer.takeRequest(1, TimeUnit.SECONDS)!!
        assertNull(recorded.getHeader("Authorization"))
    }
}

private fun coreSessionManagerWithTokens(): AuthSessionManager {
    val manager = AuthSessionManager(InterceptorRefreshTokenStore())
    manager.replace(
        AuthTokenResponse(
            tokenType = "Bearer",
            accessToken = "session-access",
            accessTokenExpiresInSeconds = 900,
            refreshToken = "session-refresh",
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
    return manager
}

private class InterceptorRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}
