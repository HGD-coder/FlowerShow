package com.example.flower_show

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.flower_show.ui.screen.*
import com.example.flower_show.ui.theme.FlowerShowTheme

/**
 * MainActivity — Single Activity, Compose-based navigation.
 *
 * Route format:
 *   "video"          → video feed (default)
 *   "search"         → search intermediate page
 *   "result:<word>"  → search results for keyword
 *   "video:<id>"     → video feed, jump to specific video (from search result)
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FlowerShowTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    var route by remember { mutableStateOf("video") }

    when {
        route == "video" -> VideoScreen(
            targetVideoId = null,
            onSearchClick = { route = "search" },
            onRecommendWordClick = { word -> route = "result:$word" },
        )

        route.startsWith("video:") -> {
            val videoId = route.removePrefix("video:")
            VideoScreen(
                targetVideoId = videoId,
                onSearchClick = { route = "search" },
                onRecommendWordClick = { word -> route = "result:$word" },
            )
        }

        route == "search" -> SearchScreen(
            onBack = { route = "video" },
            onSearch = { keyword -> route = "result:$keyword" },
        )

        route.startsWith("result:") -> {
            val keyword = route.removePrefix("result:")
            SearchResultScreen(
                keyword = keyword,
                onBack = { route = "video" },
                onResultClick = { videoId -> route = "video:$videoId" },
            )
        }
    }
}
