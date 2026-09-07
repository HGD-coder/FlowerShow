package com.example.flower_show.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.AuthIntent
import com.example.flower_show.viewmodel.AuthState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreen_rendersFieldsAndDispatchesMviIntents() {
        val intents = mutableListOf<AuthIntent>()
        composeRule.setContent {
            var state by remember { mutableStateOf(AuthState(isInitializing = false)) }
            FlowerShowTheme {
                LoginScreen(
                    state = state,
                    onIntent = { intent ->
                        intents += intent
                        state = when (intent) {
                            is AuthIntent.UsernameChanged -> state.copy(username = intent.value)
                            is AuthIntent.PasswordChanged -> state.copy(password = intent.value)
                            else -> state
                        }
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_username").assertExists()
            .performTextInput("garden.user")
        composeRule.onNodeWithTag("auth_password").assertExists()
            .performTextInput("Garden@123")
        composeRule.onNodeWithTag("auth_password").performImeAction()
        composeRule.onNodeWithTag("auth_switch_register").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(intents.contains(AuthIntent.UsernameChanged("garden.user")))
            assertTrue(intents.contains(AuthIntent.PasswordChanged("Garden@123")))
            assertTrue("Missing Submit in $intents", intents.contains(AuthIntent.Submit))
            assertTrue("Missing ShowRegister in $intents", intents.contains(AuthIntent.ShowRegister))
        }
    }

    @Test
    fun registerScreen_rendersEveryFieldAndDispatchesSwitchIntent() {
        val intents = mutableListOf<AuthIntent>()
        composeRule.setContent {
            FlowerShowTheme {
                RegisterScreen(
                    state = AuthState(isInitializing = false),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("register_screen").assertIsDisplayed()
        listOf(
            "auth_username",
            "auth_password",
            "auth_nickname",
            "auth_avatar_url",
            "auth_bio",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule.onNodeWithTag("auth_nickname")
            .performScrollTo()
            .performTextInput("花园用户")
        composeRule.onNodeWithTag("auth_switch_login")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(intents.contains(AuthIntent.NicknameChanged("花园用户")))
            assertTrue(intents.contains(AuthIntent.ShowLogin))
        }
    }

    @Test
    fun loginScreen_showsFieldAndRequestErrors() {
        composeRule.setContent {
            FlowerShowTheme {
                LoginScreen(
                    state = AuthState(
                        isInitializing = false,
                        fieldErrors = mapOf(
                            AuthField.Username to "username-error-marker",
                            AuthField.Password to "password-error-marker",
                        ),
                        message = "request-error-marker",
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("username-error-marker")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("password-error-marker")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("auth_message")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("request-error-marker").assertExists()
    }

    @Test
    fun loginScreen_submittingDisablesInputsAndShowsLoadingState() {
        composeRule.setContent {
            FlowerShowTheme {
                LoginScreen(
                    state = AuthState(
                        isInitializing = false,
                        isSubmitting = true,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("auth_username").assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_password").assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_switch_register").assertIsNotEnabled()
        composeRule.onNodeWithText("正在登录").assertExists()
    }

    @Test
    fun passwordVisibilityToggle_changesUiStateAndDispatchesIntent() {
        val intents = mutableListOf<AuthIntent>()
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    AuthState(
                        password = "Garden@123",
                        isInitializing = false,
                    ),
                )
            }
            FlowerShowTheme {
                LoginScreen(
                    state = state,
                    onIntent = { intent ->
                        intents += intent
                        if (intent == AuthIntent.TogglePasswordVisibility) {
                            state = state.copy(passwordVisible = !state.passwordVisible)
                        }
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("显示密码").performClick()
        composeRule.onNodeWithContentDescription("隐藏密码").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(intents.contains(AuthIntent.TogglePasswordVisibility))
        }
    }

    @Test
    fun authLoadingScreen_hasStableSemanticsTag() {
        composeRule.setContent {
            FlowerShowTheme {
                AuthLoadingScreen()
            }
        }

        composeRule.onNodeWithTag("auth_loading_screen").assertIsDisplayed()
    }
}
