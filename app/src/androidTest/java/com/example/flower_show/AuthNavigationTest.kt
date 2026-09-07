package com.example.flower_show

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.flower_show.data.auth.AuthApiException
import com.example.flower_show.data.auth.AuthComponents
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.data.auth.AuthRepository
import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthTokenResponse
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.auth.LoginRequest
import com.example.flower_show.data.auth.RefreshTokenStore
import com.example.flower_show.data.auth.RegisterRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {
    private val fixture = AuthNavigationFixture().also { AuthGraph.installForTests(it.components) }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForFeed() {
        waitForTag("video_screen")
        waitForTag("bottom_profile_button")
    }

    @After
    fun resetAuthGraph() {
        AuthGraph.resetForTests()
    }

    @Test
    fun loginOpensPendingProfileAndSessionLossRemovesProtectedBackStack() {
        composeRule.onNodeWithTag("bottom_profile_button", useUnmergedTree = true).performClick()
        waitForTag("login_screen")

        composeRule.onNodeWithTag("auth_username", useUnmergedTree = true)
            .performTextInput("Garden.User")
        composeRule.onNodeWithTag("auth_password", useUnmergedTree = true)
            .performTextInput("Garden@123")
        composeRule.onNodeWithTag("auth_password", useUnmergedTree = true).performImeAction()

        waitForTag("my_profile_screen")
        composeRule.runOnIdle { fixture.sessionManager.clear() }
        waitForTag("login_screen")

        pressBack()
        waitForTag("video_screen")
        composeRule.onNodeWithTag("bottom_profile_button", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun loginPageSwitchesToRegistrationPageThroughMviIntent() {
        composeRule.onNodeWithTag("bottom_profile_button", useUnmergedTree = true).performClick()
        waitForTag("login_screen")

        composeRule.onNodeWithTag("auth_switch_register", useUnmergedTree = true).performClick()

        waitForTag("register_screen")
        composeRule.onNodeWithTag("auth_nickname", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth_avatar_url", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth_bio", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun keyboardResizesAuthContentOnlyOnce() {
        composeRule.onNodeWithTag("bottom_profile_button", useUnmergedTree = true).performClick()
        waitForTag("login_screen")

        val scrollContent = composeRule.onNodeWithTag(
            "auth_scroll_content",
            useUnmergedTree = true,
        )
        val heightBeforeIme = scrollContent.fetchSemanticsNode().boundsInRoot.height

        composeRule.onNodeWithTag("auth_username", useUnmergedTree = true).performClick()
        composeRule.waitUntil(DefaultTimeoutMs) {
            scrollContent.fetchSemanticsNode().boundsInRoot.height < heightBeforeIme * 0.9f
        }

        val heightWithIme = scrollContent.fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "IME must not be deducted twice: before=$heightBeforeIme, after=$heightWithIme",
            heightWithIme >= heightBeforeIme * 0.45f,
        )
        composeRule.onNodeWithTag("auth_password", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(DefaultTimeoutMs) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        const val DefaultTimeoutMs = 30_000L
    }
}

private class AuthNavigationFixture {
    private val tokenStore = AuthNavigationTokenStore()
    val sessionManager = AuthSessionManager(tokenStore)
    private val user = AuthUser(
        userId = "server-user-id",
        accountId = "server-account-id",
        username = "garden.user",
        nickname = "花园用户",
        avatarUrl = null,
        role = "USER",
    )

    val components = AuthComponents(
        sessionManager = sessionManager,
        repository = object : AuthRepository {
            override suspend fun register(request: RegisterRequest): Result<AuthUser> =
                Result.failure(UnsupportedOperationException())

            override suspend fun login(request: LoginRequest): Result<AuthUser> {
                sessionManager.replace(tokenResponse())
                return Result.success(user)
            }

            override suspend fun restoreSession(): Result<AuthUser> =
                Result.failure(AuthApiException(401, "No refresh token"))

            override suspend fun logout(): Result<Unit> {
                sessionManager.clear()
                return Result.success(Unit)
            }

            override suspend fun logoutAll(): Result<Unit> = logout()
        },
        authenticatedClient = OkHttpClient(),
        chatRepository = TestChatRepository(),
    )

    private fun tokenResponse() = AuthTokenResponse(
        tokenType = "Bearer",
        accessToken = "access-token",
        accessTokenExpiresInSeconds = 900,
        refreshToken = "refresh-token",
        refreshTokenExpiresInSeconds = 2_592_000,
        user = user,
    )
}

private class AuthNavigationTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}
