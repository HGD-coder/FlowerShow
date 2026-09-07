package com.example.flower_show.data.auth

import com.example.flower_show.data.push.PushRegistrationCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: AuthSessionManager,
    private val refreshCoordinator: TokenRefreshCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pushRegistrationCoordinator: PushRegistrationCoordinator? = null,
) : AuthRepository {
    override suspend fun register(request: RegisterRequest): Result<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            val response = authApi.register(request)
            sessionManager.replace(response)
            pushRegistrationCoordinator?.syncStoredRegistration()
            response.user
        }
    }

    override suspend fun login(request: LoginRequest): Result<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            val response = authApi.login(request)
            sessionManager.replace(response)
            pushRegistrationCoordinator?.syncStoredRegistration()
            response.user
        }
    }

    override suspend fun restoreSession(): Result<AuthUser> = withContext(ioDispatcher) {
        runCatching {
            // 刷新统一走协调器：与 authenticator 的 401 刷新共用同一把锁，
            // 避免“启动恢复”与“请求刷新”并发竞争同一个 refresh token。
            val refreshed = refreshCoordinator.refreshStoredSession()
            if (refreshed == null) {
                // 刷新失败后若 refresh token 仍在（网络/服务端临时错误），
                // 用 5xx 语义保留“暂时无法恢复登录”的提示；token 被清说明会话已失效。
                val sessionStillPresent = sessionManager.currentRefreshToken() != null
                throw AuthApiException(
                    if (sessionStillPresent) 500 else 401,
                    "Session restore failed",
                )
            }
            sessionManager.currentSession()?.user
                ?: throw AuthApiException(401, "Session restore failed")
        }
    }

    override suspend fun logout(): Result<Unit> {
        val snapshot = sessionManager.currentSession()
        // 设备注销必须发生在 sessionManager.clear() 之前：
        // disableCurrentDevice 的请求跑在 authenticatedClient 上，AuthInterceptor
        // 会用"当前会话"的 token 覆盖请求头；会话已清空时它反而会移除手动传入的
        // Authorization 头，导致 401、服务端设备记录残留（登出后仍收推送）。
        pushRegistrationCoordinator?.disableCurrentDevice(snapshot?.accessToken)
        sessionManager.clear()
        if (snapshot == null) return Result.success(Unit)
        return withContext(ioDispatcher) {
            runCatching {
                try {
                    authApi.logout(snapshot.accessToken, snapshot.refreshToken)
                } catch (error: AuthApiException) {
                    if (error.statusCode != 401) throw error
                    // 兜底刷新也走协调器串行化，防止与正在进行的刷新
                    // 竞争同一个 refresh token（服务端轮换时双方互相作废）。
                    val refreshed = refreshCoordinator.refreshWithExplicitToken(snapshot.refreshToken)
                        ?: throw AuthApiException(401, "Refresh token expired during logout")
                    authApi.logout(refreshed.accessToken, refreshed.refreshToken)
                }
                Unit
            }
        }
    }

    override suspend fun logoutAll(): Result<Unit> {
        val snapshot = sessionManager.currentSession()
        // 同 logout()：先注销推送设备，再清会话，否则注销请求会丢失凭证。
        pushRegistrationCoordinator?.disableCurrentDevice(snapshot?.accessToken)
        sessionManager.clear()
        if (snapshot == null) return Result.success(Unit)
        return withContext(ioDispatcher) {
            runCatching {
                try {
                    authApi.logoutAll(snapshot.accessToken)
                } catch (error: AuthApiException) {
                    if (error.statusCode != 401) throw error
                    val refreshed = refreshCoordinator.refreshWithExplicitToken(snapshot.refreshToken)
                        ?: throw AuthApiException(401, "Refresh token expired during logout")
                    authApi.logoutAll(refreshed.accessToken)
                }
                Unit
            }
        }
    }
}
