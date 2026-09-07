package com.example.flower_show.viewmodel

import com.example.flower_show.data.auth.AuthApiException
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.data.auth.AuthRepository
import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthTokenResponse
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.auth.LoginRequest
import com.example.flower_show.data.auth.RefreshTokenStore
import com.example.flower_show.data.auth.RegisterRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule: TestWatcher = MainDispatcherRule()

    @Test
    fun intentsAreTheOnlyInputAndSynchronouslyProduceScreenState() = runTest {
        val fixture = createFixture()
        runCurrent()

        fixture.viewModel.dispatch(AuthIntent.ShowRegister)
        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("Garden.User"))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("Garden@123"))
        fixture.viewModel.dispatch(AuthIntent.NicknameChanged("花园用户"))
        fixture.viewModel.dispatch(AuthIntent.AvatarUrlChanged("https://example.com/avatar.jpg"))
        fixture.viewModel.dispatch(AuthIntent.BioChanged("阳台种花"))
        fixture.viewModel.dispatch(AuthIntent.TogglePasswordVisibility)

        assertEquals(
            AuthState(
                mode = AuthMode.Register,
                username = "Garden.User",
                password = "Garden@123",
                nickname = "花园用户",
                avatarUrl = "https://example.com/avatar.jpg",
                bio = "阳台种花",
                passwordVisible = true,
                isInitializing = false,
            ),
            fixture.viewModel.state.value,
        )

        fixture.viewModel.dispatch(AuthIntent.ShowLogin)

        assertEquals(AuthMode.Login, fixture.viewModel.state.value.mode)
        assertEquals("", fixture.viewModel.state.value.password)
        assertFalse(fixture.viewModel.state.value.passwordVisible)
        assertTrue(fixture.viewModel.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun invalidLoginAndRegistrationAreRejectedBeforeRepositoryCalls() = runTest {
        val fixture = createFixture()
        runCurrent()

        fixture.viewModel.dispatch(AuthIntent.UsernameChanged(" "))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged(""))
        fixture.viewModel.dispatch(AuthIntent.Submit)

        assertEquals(
            setOf(AuthField.Username, AuthField.Password),
            fixture.viewModel.state.value.fieldErrors.keys,
        )
        assertEquals(0, fixture.repository.loginCalls)
        assertFalse(fixture.viewModel.state.value.isSubmitting)

        fixture.viewModel.dispatch(AuthIntent.ShowRegister)
        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("not allowed"))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("short"))
        fixture.viewModel.dispatch(AuthIntent.NicknameChanged(" "))
        fixture.viewModel.dispatch(AuthIntent.BioChanged("花".repeat(256)))
        fixture.viewModel.dispatch(AuthIntent.Submit)

        assertEquals(
            setOf(
                AuthField.Username,
                AuthField.Password,
                AuthField.Nickname,
                AuthField.Bio,
            ),
            fixture.viewModel.state.value.fieldErrors.keys,
        )
        assertEquals(0, fixture.repository.registerCalls)
        assertFalse(fixture.viewModel.state.value.isSubmitting)
    }

    @Test
    fun registrationNormalizesRequestSuppressesDuplicateSubmitAndUsesSessionUser() = runTest {
        val fixture = createFixture()
        val resultGate = CompletableDeferred<Result<AuthUser>>()
        val sessionUser = authUser(
            userId = "user-from-jwt-response",
            username = "garden.user",
            nickname = "服务端花园用户",
        )
        val repositoryResultUser = authUser(
            userId = "repository-result",
            username = "ignored.result",
            nickname = "不应进入状态",
        )
        fixture.repository.registerHandler = {
            resultGate.await().also { result ->
                if (result.isSuccess) {
                    fixture.sessionManager.replace(tokenResponse(sessionUser))
                }
            }
        }
        runCurrent()

        fixture.viewModel.dispatch(AuthIntent.ShowRegister)
        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("  Garden.User  "))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("Garden@123"))
        fixture.viewModel.dispatch(AuthIntent.NicknameChanged("  花园用户  "))
        fixture.viewModel.dispatch(AuthIntent.AvatarUrlChanged("   "))
        fixture.viewModel.dispatch(AuthIntent.BioChanged("  阳台种花  "))
        fixture.viewModel.dispatch(AuthIntent.Submit)
        fixture.viewModel.dispatch(AuthIntent.Submit)

        assertTrue(fixture.viewModel.state.value.isSubmitting)
        runCurrent()
        assertEquals(1, fixture.repository.registerCalls)
        assertEquals(
            RegisterRequest(
                username = "garden.user",
                password = "Garden@123",
                nickname = "花园用户",
                avatarUrl = null,
                bio = "阳台种花",
            ),
            fixture.repository.lastRegisterRequest,
        )

        resultGate.complete(Result.success(repositoryResultUser))
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals("", state.password)
        assertEquals("garden.user", state.username)
        assertSame(sessionUser, state.currentUser)
        assertEquals("user-from-jwt-response", fixture.sessionManager.currentUserId())
    }

    @Test
    fun loginSuccessClearsPasswordAndUsesUserIdFromSessionResponse() = runTest {
        val fixture = createFixture()
        val sessionUser = authUser(
            userId = "authenticated-user-id",
            username = "garden.user",
            nickname = "花园用户",
        )
        fixture.repository.loginHandler = { request ->
            fixture.sessionManager.replace(tokenResponse(sessionUser))
            Result.success(sessionUser)
        }
        runCurrent()

        fixture.viewModel.dispatch(AuthIntent.UsernameChanged(" Garden.User "))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("Garden@123"))
        fixture.viewModel.dispatch(AuthIntent.Submit)
        advanceUntilIdle()

        assertEquals(
            LoginRequest(username = "garden.user", password = "Garden@123"),
            fixture.repository.lastLoginRequest,
        )
        assertEquals("", fixture.viewModel.state.value.password)
        assertSame(sessionUser, fixture.viewModel.state.value.currentUser)
        assertEquals("authenticated-user-id", fixture.sessionManager.currentUserId())
    }

    @Test
    fun httpStatusFailuresMapToExpectedStateAndClearPassword() = runTest {
        val fixture = createFixture()
        val expectedMessages = linkedMapOf(
            400 to "请检查填写内容是否符合要求",
            401 to "用户名或密码错误",
            403 to "账号不可用或身份不匹配",
            409 to "用户名或同名账号已存在",
            429 to "连续失败过多，账号已锁定 15 分钟",
        )
        var statusCode = 400
        fixture.repository.loginHandler = {
            Result.failure(AuthApiException(statusCode, "server message must not leak"))
        }
        runCurrent()
        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("garden.user"))

        expectedMessages.forEach { (status, expectedMessage) ->
            statusCode = status
            fixture.viewModel.dispatch(AuthIntent.PasswordChanged("Garden@123"))
            fixture.viewModel.dispatch(AuthIntent.Submit)
            runCurrent()

            val state = fixture.viewModel.state.value
            assertEquals("status $status", expectedMessage, state.message)
            assertEquals("status $status", "", state.password)
            assertFalse("status $status", state.isSubmitting)
            if (status == 409) {
                assertEquals(expectedMessage, state.fieldErrors[AuthField.Username])
            } else {
                assertTrue("status $status", state.fieldErrors.isEmpty())
            }
        }

        assertEquals(expectedMessages.size, fixture.repository.loginCalls)
        fixture.viewModel.dispatch(AuthIntent.DismissMessage)
        assertNull(fixture.viewModel.state.value.message)
    }

    @Test
    fun changingAFieldClearsItsServerErrorAndMessageThroughIntent() = runTest {
        val fixture = createFixture()
        fixture.repository.loginHandler = {
            Result.failure(AuthApiException(409, "conflict"))
        }
        runCurrent()
        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("garden.user"))
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("Garden@123"))
        fixture.viewModel.dispatch(AuthIntent.Submit)
        runCurrent()

        assertTrue(AuthField.Username in fixture.viewModel.state.value.fieldErrors)
        assertEquals("用户名或同名账号已存在", fixture.viewModel.state.value.message)

        fixture.viewModel.dispatch(AuthIntent.UsernameChanged("new.garden.user"))

        assertFalse(AuthField.Username in fixture.viewModel.state.value.fieldErrors)
        assertNull(fixture.viewModel.state.value.message)
    }

    @Test
    fun logoutAndLogoutAllClearAuthenticatedStateThroughRepository() = runTest {
        val fixture = createFixture()
        fixture.repository.logoutHandler = {
            fixture.sessionManager.clear()
            Result.success(Unit)
        }
        fixture.repository.logoutAllHandler = {
            fixture.sessionManager.clear()
            Result.success(Unit)
        }
        runCurrent()

        fixture.sessionManager.replace(tokenResponse(authUser(userId = "device-one")))
        runCurrent()
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("not-retained"))
        fixture.viewModel.dispatch(AuthIntent.Logout)
        assertTrue(fixture.viewModel.state.value.isSubmitting)
        advanceUntilIdle()

        assertEquals(1, fixture.repository.logoutCalls)
        assertEquals(0, fixture.repository.logoutAllCalls)
        assertNull(fixture.viewModel.state.value.currentUser)
        assertEquals("", fixture.viewModel.state.value.password)
        assertFalse(fixture.viewModel.state.value.isSubmitting)

        fixture.sessionManager.replace(tokenResponse(authUser(userId = "all-devices")))
        runCurrent()
        fixture.viewModel.dispatch(AuthIntent.PasswordChanged("not-retained-either"))
        fixture.viewModel.dispatch(AuthIntent.LogoutAll)
        advanceUntilIdle()

        assertEquals(1, fixture.repository.logoutCalls)
        assertEquals(1, fixture.repository.logoutAllCalls)
        assertNull(fixture.viewModel.state.value.currentUser)
        assertNull(fixture.sessionManager.currentUserId())
        assertEquals("", fixture.viewModel.state.value.password)
        assertFalse(fixture.viewModel.state.value.isSubmitting)
    }

    private fun createFixture(): Fixture {
        val tokenStore = InMemoryRefreshTokenStore()
        val sessionManager = AuthSessionManager(tokenStore)
        val repository = FakeAuthRepository()
        return Fixture(
            viewModel = AuthViewModel(repository, sessionManager),
            repository = repository,
            sessionManager = sessionManager,
        )
    }

    private data class Fixture(
        val viewModel: AuthViewModel,
        val repository: FakeAuthRepository,
        val sessionManager: AuthSessionManager,
    )

    private class FakeAuthRepository : AuthRepository {
        var registerCalls = 0
        var loginCalls = 0
        var restoreCalls = 0
        var logoutCalls = 0
        var logoutAllCalls = 0

        var lastRegisterRequest: RegisterRequest? = null
        var lastLoginRequest: LoginRequest? = null

        var registerHandler: suspend (RegisterRequest) -> Result<AuthUser> = {
            Result.failure(AssertionError("Unexpected register call"))
        }
        var loginHandler: suspend (LoginRequest) -> Result<AuthUser> = {
            Result.failure(AssertionError("Unexpected login call"))
        }
        var restoreHandler: suspend () -> Result<AuthUser> = {
            Result.failure(AuthApiException(401, "No stored refresh token"))
        }
        var logoutHandler: suspend () -> Result<Unit> = {
            Result.failure(AssertionError("Unexpected logout call"))
        }
        var logoutAllHandler: suspend () -> Result<Unit> = {
            Result.failure(AssertionError("Unexpected logout-all call"))
        }

        override suspend fun register(request: RegisterRequest): Result<AuthUser> {
            registerCalls++
            lastRegisterRequest = request
            return registerHandler(request)
        }

        override suspend fun login(request: LoginRequest): Result<AuthUser> {
            loginCalls++
            lastLoginRequest = request
            return loginHandler(request)
        }

        override suspend fun restoreSession(): Result<AuthUser> {
            restoreCalls++
            return restoreHandler()
        }

        override suspend fun logout(): Result<Unit> {
            logoutCalls++
            return logoutHandler()
        }

        override suspend fun logoutAll(): Result<Unit> {
            logoutAllCalls++
            return logoutAllHandler()
        }
    }

    private class InMemoryRefreshTokenStore : RefreshTokenStore {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(refreshToken: String) {
            value = refreshToken
        }

        override fun clear() {
            value = null
        }
    }

    private fun authUser(
        userId: String,
        username: String = "garden.user",
        nickname: String = "花园用户",
    ): AuthUser = AuthUser(
        userId = userId,
        accountId = "account-$userId",
        username = username,
        nickname = nickname,
        avatarUrl = null,
        role = "USER",
    )

    private fun tokenResponse(user: AuthUser): AuthTokenResponse = AuthTokenResponse(
        tokenType = "Bearer",
        accessToken = "access-${user.userId}",
        accessTokenExpiresInSeconds = 15 * 60,
        refreshToken = "refresh-${user.userId}",
        refreshTokenExpiresInSeconds = 30L * 24 * 60 * 60,
        user = user,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
