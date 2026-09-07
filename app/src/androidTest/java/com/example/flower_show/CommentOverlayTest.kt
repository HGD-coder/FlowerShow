package com.example.flower_show

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.flower_show.model.VideoComment
import com.example.flower_show.ui.component.CommentOverlay
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.CommentState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CommentOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForHomeFeed() {
        waitForTag("video_screen", DefaultTimeoutMs)
        waitForTag("home_search_button", DefaultTimeoutMs)
    }

    @Test
    fun commentOverlayKeepsPlayerMountedAndClosesWithoutLeavingFeed() {
        openKnownVideo()
        waitForTag("player_surface", SearchTimeoutMs)
        waitForTag("comment_video_button", SearchTimeoutMs)

        composeRule.onNodeWithTag("comment_video_button", useUnmergedTree = true)
            .performClick()

        waitForTag("comment_sheet")
        waitForTag("comment_scrim")
        composeRule.onNodeWithTag("video_screen", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("player_surface", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("comment_close_button", useUnmergedTree = true)
            .performClick()
        composeRule.waitUntil(DefaultTimeoutMs) {
            composeRule.onAllNodesWithTag("comment_sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag("comment_sheet", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("video_screen", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("player_surface", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun openKnownVideo() {
        composeRule.onNodeWithTag("home_search_button", useUnmergedTree = true)
            .performClick()
        waitForTag("search_screen")
        composeRule.onNodeWithTag("search_input", useUnmergedTree = true)
            .performTextInput("虾")
        composeRule.onNodeWithTag("search_submit_button", useUnmergedTree = true)
            .performClick()
        waitForTag("search_result_screen", SearchTimeoutMs)
        composeRule.waitUntil(SearchTimeoutMs) {
            composeRule.onAllNodes(VideoResultRowMatcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodes(VideoResultRowMatcher, useUnmergedTree = true)[0]
            .performClick()
        waitForTag("video_screen", SearchTimeoutMs)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = DefaultTimeoutMs) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        const val DefaultTimeoutMs = 30_000L
        const val SearchTimeoutMs = 45_000L
        const val VideoResultRowTagPrefix = "search_result_row_video:"

        val VideoResultRowMatcher = SemanticsMatcher("has video search result row test tag") { node ->
            node.config
                .testTagOrNull()
                ?.startsWith(VideoResultRowTagPrefix) == true
        }

        private fun androidx.compose.ui.semantics.SemanticsConfiguration.testTagOrNull(): String? {
            return runCatching { this[SemanticsProperties.TestTag] }.getOrNull()
        }
    }
}

@LargeTest
@RunWith(AndroidJUnit4::class)
class CommentOverlayInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sendingCommentShowsTrimmedTextAndClearsInput() {
        var state by mutableStateOf(
            CommentState(
                visible = true,
                videoId = "direct-video",
                totalCount = 12,
            ),
        )

        composeRule.setContent {
            FlowerShowTheme {
                CommentOverlay(
                    state = state,
                    onDismiss = { state = state.copy(visible = false) },
                    onDraftChanged = { value -> state = state.copy(draft = value) },
                    onSend = {
                        val content = state.draft.trim()
                        if (content.isNotEmpty()) {
                            state = state.copy(
                                totalCount = state.totalCount + 1,
                                comments = listOf(
                                    VideoComment(
                                        id = "local-test-comment",
                                        author = "我",
                                        content = content,
                                        timeLabel = "刚刚",
                                        isMine = true,
                                    ),
                                ) + state.comments,
                                draft = "",
                            )
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("comment_sheet", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("comment_input", useUnmergedTree = true)
            .performTextInput("  $NewComment  ")
        composeRule.onNodeWithTag("comment_send_button", useUnmergedTree = true)
            .performClick()

        composeRule.onNodeWithText(NewComment, useUnmergedTree = true)
            .assertIsDisplayed()
        val inputNode = composeRule.onNodeWithTag("comment_input", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(
            "",
            inputNode.config[SemanticsProperties.EditableText].text,
        )
    }

    private companion object {
        const val NewComment = "这是一条评论区测试消息"
    }
}
