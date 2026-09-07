package com.example.flower_show

import com.example.flower_show.data.auth.AuthComponents
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.data.auth.AuthRepository
import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthTokenResponse
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.auth.LoginRequest
import com.example.flower_show.data.auth.RefreshTokenStore
import com.example.flower_show.data.auth.RegisterRequest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.OkHttpClient

@LargeTest
@RunWith(AndroidJUnit4::class)
class SocialNavigationTest {
    private val authenticatedComponents = createAuthenticatedComponents().also {
        AuthGraph.installForTests(it)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetAuthGraph() {
        AuthGraph.resetForTests()
    }

    @Before
    fun waitForFeed() {
        waitForTag("video_screen")
        waitForTag("bottom_profile_button")
    }

    @Test
    fun myProfileShowsTabsAndOpensFollowersList() {
        composeRule.onNodeWithTag("bottom_profile_button", useUnmergedTree = true).performClick()

        waitForTag("my_profile_screen")
        composeRule.onNodeWithTag("profile_tab_works", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tab_likes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tab_collections", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("profile_followers_stat", useUnmergedTree = true).performClick()

        waitForTag("connections_screen")
        composeRule.onNodeWithTag("connections_search_input", useUnmergedTree = true)
            .performTextInput("阿遥")
        composeRule.onNodeWithTag("connection_user_user-ayao", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun messagesOpenMutualFriendChatAndSendText() {
        composeRule.onNodeWithTag("bottom_messages_button", useUnmergedTree = true).performClick()

        waitForTag("message_list_screen")
        composeRule.onNodeWithTag("conversation_friend-linwu", useUnmergedTree = true).performClick()

        waitForTag("chat_screen")
        composeRule.onNodeWithTag("chat_input", useUnmergedTree = true).performTextInput("测试新消息")
        composeRule.onNodeWithTag("chat_send_button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(DefaultTimeoutMs) {
            composeRule.onAllNodesWithText("测试新消息", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("测试新消息").assertIsDisplayed()
    }

    @Test
    fun relationshipListOpensPublicOtherProfileWithMutualMessageAction() {
        composeRule.onNodeWithTag("bottom_friends_button", useUnmergedTree = true).performClick()
        waitForTag("connections_screen")
        composeRule.onNodeWithTag("connection_user_friend-linwu", useUnmergedTree = true)
            .performClick()

        waitForTag("other_profile_screen")
        composeRule.onNodeWithText("公开作品").assertIsDisplayed()
        composeRule.onNodeWithTag("other_profile_message_button", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("发消息").assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(DefaultTimeoutMs) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun createAuthenticatedComponents(): AuthComponents {
        val store = InMemoryRefreshTokenStore()
        val sessionManager = AuthSessionManager(store)
        val user = AuthUser(
            userId = "instrumented-user-id",
            accountId = "instrumented-account-id",
            username = "instrumented.user",
            nickname = "测试用户",
            avatarUrl = null,
            role = "USER",
        )
        sessionManager.replace(
            AuthTokenResponse(
                tokenType = "Bearer",
                accessToken = "instrumented-access",
                accessTokenExpiresInSeconds = 900,
                refreshToken = "instrumented-refresh",
                refreshTokenExpiresInSeconds = 2_592_000,
                user = user,
            ),
        )
        val repository = object : AuthRepository {
            override suspend fun register(request: RegisterRequest) =
                Result.failure<AuthUser>(UnsupportedOperationException())

            override suspend fun login(request: LoginRequest) =
                Result.failure<AuthUser>(UnsupportedOperationException())

            override suspend fun restoreSession(): Result<AuthUser> = Result.success(user)

            override suspend fun logout(): Result<Unit> {
                sessionManager.clear()
                return Result.success(Unit)
            }

            override suspend fun logoutAll(): Result<Unit> = logout()
        }
        return AuthComponents(
            sessionManager = sessionManager,
            repository = repository,
            authenticatedClient = OkHttpClient(),
            socialRepository = TestSocialRepository(),
            chatRepository = TestChatRepository(),
        )
    }

    private companion object {
        const val DefaultTimeoutMs = 30_000L
    }
}

private class InMemoryRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}
