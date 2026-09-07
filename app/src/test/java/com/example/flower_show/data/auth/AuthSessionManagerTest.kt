package com.example.flower_show.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionManagerTest {
    @Test
    fun replaceUsesServerUserIdAndPersistsOnlyRefreshToken() {
        val store = RecordingCoreRefreshTokenStore()
        val manager = AuthSessionManager(store, currentTimeMillis = { 1_000L })

        manager.replace(
            coreTokenResponse(
                accessToken = "access-secret",
                refreshToken = "refresh-secret",
                userId = "user-7f621",
                username = "garden.user",
            ),
        )

        assertEquals("user-7f621", manager.currentUserId())
        assertFalse(manager.currentUserId() == manager.currentUser.value?.username)
        assertEquals("access-secret", manager.currentAccessToken())
        assertEquals("refresh-secret", manager.currentRefreshToken())
        assertEquals("refresh-secret", store.token)
        assertEquals(16_000L, manager.currentSession()?.accessTokenExpiresAtMillis)
        assertEquals(2_592_001_000L, manager.currentSession()?.refreshTokenExpiresAtMillis)
    }

    @Test
    fun failedRefreshTokenWriteDegradesToInMemorySessionWithoutThrowing() {
        val store = RecordingCoreRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(
            coreTokenResponse(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                userId = "old-user",
            ),
        )
        store.failWrites = true

        // 持久化失败降级：内存会话照常生效（服务端已签发 token，登录不应整体失败），
        // 同时尽力清理存储，避免残留半写状态。
        manager.replace(
            coreTokenResponse(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                userId = "new-user",
            ),
        )

        assertEquals("new-access", manager.currentAccessToken())
        assertEquals("new-refresh", manager.currentRefreshToken())
        assertEquals("new-user", manager.currentUserId())
        assertNull(store.token)
    }

    @Test
    fun clearRemovesMemoryAndPersistentAuthenticationState() {
        val store = RecordingCoreRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(coreTokenResponse())

        manager.clear()

        assertNull(manager.currentAccessToken())
        assertNull(manager.currentRefreshToken())
        assertNull(manager.currentUser.value)
        assertNull(store.token)
        assertEquals(1, store.clearCount)
    }

    @Test
    fun sessionDebugTextNeverContainsEitherToken() {
        val store = RecordingCoreRefreshTokenStore()
        val manager = AuthSessionManager(store)
        manager.replace(
            coreTokenResponse(
                accessToken = "access-do-not-log",
                refreshToken = "refresh-do-not-log",
                userId = "safe-user-id",
            ),
        )

        val debugText = manager.currentSession().toString()

        assertTrue(debugText.contains("safe-user-id"))
        assertFalse(debugText.contains("access-do-not-log"))
        assertFalse(debugText.contains("refresh-do-not-log"))
    }
}

private class RecordingCoreRefreshTokenStore : RefreshTokenStore {
    var token: String? = null
    var failWrites: Boolean = false
    var clearCount: Int = 0

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        if (failWrites) throw IllegalStateException("Encrypted storage unavailable")
        token = refreshToken
    }

    override fun clear() {
        clearCount++
        token = null
    }
}

private fun coreTokenResponse(
    accessToken: String = "access-token",
    refreshToken: String = "refresh-token",
    userId: String = "user-id",
    username: String = "garden.user",
): AuthTokenResponse = AuthTokenResponse(
    tokenType = "Bearer",
    accessToken = accessToken,
    accessTokenExpiresInSeconds = 15,
    refreshToken = refreshToken,
    refreshTokenExpiresInSeconds = 2_592_000,
    user = AuthUser(
        userId = userId,
        accountId = "account-id",
        username = username,
        nickname = "花园用户",
        role = "USER",
    ),
)
