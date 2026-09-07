package com.example.flower_show.data.auth

import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val refreshCoordinator: TokenRefreshCoordinator,
    private val trustedOrigin: HttpUrl,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (!response.request.url.hasSameOrigin(trustedOrigin)) return null
        if (responseCount(response) >= MaxRequestAttempts) return null

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }
        val newAccessToken = refreshCoordinator.refreshAfterUnauthorized(failedToken) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .removeHeader("X-User-Id")
            .build()
    }

    /**
     * 只统计 priorResponse 链中的 401 次数，作为认证重试预算。
     *
     * priorResponse 链还包含重定向与连接重试：若把它们也计入预算，
     * 一个“先经历 302 再收到 401”的请求会一次刷新机会都没有。
     * 只数 401 既能给重定向后的 401 重试机会，又保证 401 重试有上限（不会死循环）。
     */
    private fun responseCount(response: Response): Int {
        var count = 0
        var current: Response? = response
        while (current != null) {
            if (current.code == 401) count++
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        const val MaxRequestAttempts = 2
    }
}
