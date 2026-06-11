package com.example.flower_show

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UiNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForHomeFeed() {
        waitForTag("video_screen", DefaultTimeoutMs)
        waitForTag("home_search_button", DefaultTimeoutMs)
    }

    @Test
    fun homeSearchButtonOpensSearchScreenAndBackReturnsToFeed() {
        composeRule.onNodeWithTag("home_search_button", useUnmergedTree = true)
            .performClick()

        waitForTag("search_screen")
        composeRule.onNodeWithTag("search_back_button", useUnmergedTree = true)
            .performClick()

        waitForTag("video_screen")
        composeRule.onNodeWithTag("home_search_button", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun searchKeywordNavigatesToResultScreenAndShowsRows() {
        openSearchScreen()
        submitSearch("虾")

        waitForTag("search_result_screen", SearchTimeoutMs)
        waitForFirstResultRow()
    }

    @Test
    fun clickingSearchResultNavigatesToThatVideoCard() {
        openSearchScreen()
        submitSearch("虾")
        waitForFirstResultRow()

        val targetVideoId = firstResultVideoId()
        val resultRows = composeRule.onAllNodes(ResultRowMatcher, useUnmergedTree = true)
        resultRows[0].performClick()

        waitForTag("video_card_$targetVideoId", SearchTimeoutMs)
        composeRule.onNodeWithTag("video_card_$targetVideoId", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun openSearchScreen() {
        composeRule.onNodeWithTag("home_search_button", useUnmergedTree = true)
            .performClick()
        waitForTag("search_screen")
    }

    private fun submitSearch(keyword: String) {
        composeRule.onNodeWithTag("search_input", useUnmergedTree = true)
            .performTextInput(keyword)
        composeRule.onNodeWithTag("search_submit_button", useUnmergedTree = true)
            .performClick()
        waitForTag("search_result_screen", SearchTimeoutMs)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = DefaultTimeoutMs) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForFirstResultRow() {
        composeRule.waitUntil(SearchTimeoutMs) {
            composeRule.onAllNodes(ResultRowMatcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun firstResultVideoId(): String {
        val firstNode = composeRule.onAllNodes(ResultRowMatcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
        return firstNode.config
            .testTagOrNull()
            .orEmpty()
            .removePrefix(ResultRowTagPrefix)
    }

    private companion object {
        const val DefaultTimeoutMs = 30_000L
        const val SearchTimeoutMs = 45_000L
        const val ResultRowTagPrefix = "search_result_row_"

        val ResultRowMatcher = SemanticsMatcher("has search result row test tag") { node ->
            node.config
                .testTagOrNull()
                ?.startsWith(ResultRowTagPrefix) == true
        }

        private fun androidx.compose.ui.semantics.SemanticsConfiguration.testTagOrNull(): String? {
            return runCatching { this[SemanticsProperties.TestTag] }.getOrNull()
        }
    }
}
