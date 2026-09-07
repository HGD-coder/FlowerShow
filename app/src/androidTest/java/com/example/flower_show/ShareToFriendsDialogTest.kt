package com.example.flower_show

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.ui.screen.ShareToFriendsDialog
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.ChatIntent
import com.example.flower_show.viewmodel.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShareToFriendsDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogListsMutualContactsAndSendsThroughChatRepository() {
        val chatRepository = TestChatRepository()
        val viewModel = ChatViewModel(
            chatRepository = chatRepository,
            socialRepository = TestSocialRepository(),
        )
        val video = ProfileVideo("video-1", "花园散步", "北屿", "")
        viewModel.dispatch(ChatIntent.BindCurrentUser("instrumented-user-id"))
        viewModel.dispatch(ChatIntent.OpenShareComposer(video))

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            FlowerShowTheme {
                state.shareComposer?.let { composer ->
                    ShareToFriendsDialog(
                        state = composer,
                        onDismiss = {
                            viewModel.dispatch(ChatIntent.DismissShareComposer)
                        },
                        onQueryChange = {
                            viewModel.dispatch(ChatIntent.UpdateShareQuery(it))
                        },
                        onFriendSelected = {
                            viewModel.dispatch(ChatIntent.SelectShareRecipient(it))
                        },
                        onSend = {
                            viewModel.dispatch(ChatIntent.SendVideoShare)
                        },
                    )
                }
            }
        }

        waitForTag("share_friends_dialog")
        waitForTag("share_friend_friend-linwu")
        composeRule.onNodeWithTag(
            "share_friend_friend-linwu",
            useUnmergedTree = true,
        ).performClick()
        assertTrue(
            composeRule.onAllNodesWithTag(
                "share_friend_user-ayao",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithTag("share_send_button", useUnmergedTree = true).performClick()

        composeRule.waitUntil(DefaultTimeoutMs) {
            viewModel.state.value.shareComposer == null && chatRepository.videoRequests.isNotEmpty()
        }
        composeRule.runOnIdle {
            val request = chatRepository.videoRequests.single()
            assertEquals("friend-linwu", request.conversationId)
            assertEquals(video.id, request.contentId)
            assertTrue(request.clientMessageId.isNotBlank())
        }
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
