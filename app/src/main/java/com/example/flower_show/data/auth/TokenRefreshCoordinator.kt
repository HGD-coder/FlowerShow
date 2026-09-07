package com.example.flower_show.data.auth

class TokenRefreshCoordinator(
    private val authApi: AuthApi,
    private val sessionManager: AuthSessionManager,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val refreshLock = Any()
    private var recentTransientFailure: TransientFailure? = null
    private var lastRefreshedAccessToken: String? = null

    fun refreshAfterUnauthorized(failedAccessToken: String?): String? = synchronized(refreshLock) {
        val latestAccessToken = sessionManager.currentAccessToken()
        if (
            !failedAccessToken.isNullOrBlank() &&
            !latestAccessToken.isNullOrBlank() &&
            failedAccessToken != latestAccessToken
        ) {
            // 只允许并发请求复用“本协调器刚刷新出来的”token；
            // 若最新 token 来自其他来源（例如期间用户登录/切换了账号），
            // 不能拿新会话的 token 重放旧请求，直接放弃重试。
            if (latestAccessToken == lastRefreshedAccessToken) {
                recentTransientFailure = null
                return latestAccessToken
            }
            return null
        }

        val failureKey = failedAccessToken ?: latestAccessToken
        val previousFailure = recentTransientFailure
        if (
            failureKey != null &&
            previousFailure?.accessToken == failureKey &&
            nanoTime() - previousFailure.failedAtNanos < TransientFailureCoalescingNanos
        ) {
            return null
        }

        return performRefresh(failureKey)
    }

    /**
     * 启动时用存储的 refresh token 恢复会话。
     *
     * 与 authenticator 的刷新共用同一把锁，避免“启动恢复”与“请求 401 刷新”
     * 并发使用同一个 refresh token（服务端轮换 token 时会导致一方失败、意外登出）。
     */
    fun refreshStoredSession(): String? = synchronized(refreshLock) {
        performRefresh(failureKey = null)
    }

    /**
     * 用调用方指定的 refresh token 刷新一次，但不改动当前会话。
     *
     * 用于登出兜底：与协调器的其他刷新串行化，防止与正在进行的刷新
     * 竞争同一个 refresh token；返回 null 表示刷新失败。
     */
    fun refreshWithExplicitToken(refreshToken: String): AuthTokenResponse? = synchronized(refreshLock) {
        try {
            authApi.refresh(refreshToken)
        } catch (_: AuthApiException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun performRefresh(failureKey: String?): String? {
        val refreshToken = sessionManager.currentRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            recentTransientFailure = null
            lastRefreshedAccessToken = null
            sessionManager.clear()
            return null
        }

        return try {
            val response = authApi.refresh(refreshToken)
            sessionManager.replace(response)
            recentTransientFailure = null
            lastRefreshedAccessToken = response.accessToken
            response.accessToken
        } catch (error: AuthApiException) {
            if (error.statusCode == 401 || error.statusCode == 403) {
                recentTransientFailure = null
                lastRefreshedAccessToken = null
                sessionManager.clear()
            } else if (failureKey != null) {
                recentTransientFailure = TransientFailure(failureKey, nanoTime())
            }
            null
        } catch (_: Exception) {
            if (failureKey != null) {
                recentTransientFailure = TransientFailure(failureKey, nanoTime())
            }
            null
        }
    }

    private data class TransientFailure(
        val accessToken: String,
        val failedAtNanos: Long,
    )

    private companion object {
        const val TransientFailureCoalescingNanos = 2_000_000_000L
    }
}
