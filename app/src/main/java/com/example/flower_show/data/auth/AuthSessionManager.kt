package com.example.flower_show.data.auth

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthSessionManager(
    private val refreshTokenStore: RefreshTokenStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var session: SessionSnapshot? = null
    private val _currentUser = MutableStateFlow<AuthUser?>(null)

    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    fun currentUserId(): String? = _currentUser.value?.userId

    fun hasStoredRefreshToken(): Boolean = refreshTokenStore.read() != null

    internal fun currentAccessToken(): String? = synchronized(lock) {
        session?.accessToken
    }

    internal fun currentRefreshToken(): String? = synchronized(lock) {
        session?.refreshToken ?: refreshTokenStore.read()
    }

    internal fun currentSession(): SessionSnapshot? = synchronized(lock) { session }

    internal fun replace(response: AuthTokenResponse) = synchronized(lock) {
        require(response.tokenType.equals("Bearer", ignoreCase = true)) { "Unsupported token type" }
        require(response.accessToken.isNotBlank()) { "Access token must not be blank" }
        require(response.refreshToken.isNotBlank()) { "Refresh token must not be blank" }
        require(response.user.userId.isNotBlank()) { "Authenticated user id must not be blank" }

        // Persist first. 部分设备的 KeyStore 会抛异常：持久化失败时降级为
        // “清掉存储 + 继续使用内存会话”，而不是让本次登录/刷新整体失败
        // （服务端此时可能已经签发了新 token，丢弃会导致下次启动无法恢复会话）。
        runCatching { refreshTokenStore.write(response.refreshToken) }
            .onFailure { error ->
                logW("Failed to persist refresh token; keeping in-memory session only", error)
                runCatching { refreshTokenStore.clear() }
                    .onFailure { clearError ->
                        logW("Failed to clear stale refresh token storage", clearError)
                    }
            }
        session = SessionSnapshot(
            accessToken = response.accessToken,
            accessTokenExpiresAtMillis = currentTimeMillis() +
                response.accessTokenExpiresInSeconds.coerceAtLeast(0) * 1_000,
            refreshToken = response.refreshToken,
            refreshTokenExpiresAtMillis = currentTimeMillis() +
                response.refreshTokenExpiresInSeconds.coerceAtLeast(0) * 1_000,
            user = response.user,
        )
        _currentUser.value = response.user
    }

    fun clear() = synchronized(lock) {
        session = null
        runCatching { refreshTokenStore.clear() }
            .onFailure { error -> logW("Failed to clear refresh token storage", error) }
        _currentUser.value = null
    }

    // JVM 单元测试中 android.util.Log 未 mock，直接调用会抛异常；
    // 与 PerformanceDiagnostics 一致用 runCatching 包裹。
    private fun logW(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.w(TAG, message, error)
            } else {
                Log.w(TAG, message)
            }
        }
    }

    private companion object {
        const val TAG = "AuthSessionManager"
    }
}

internal class SessionSnapshot(
    val accessToken: String,
    val accessTokenExpiresAtMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtMillis: Long,
    val user: AuthUser,
) {
    override fun toString(): String = "SessionSnapshot(userId=${user.userId})"
}
