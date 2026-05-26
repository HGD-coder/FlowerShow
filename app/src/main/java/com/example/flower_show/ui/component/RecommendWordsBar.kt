package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RecommendWordsBar — Horizontal scrolling recommend word chips.
 * 推荐词栏 — 水平滚动推荐词标签
 */
@Composable
fun RecommendWordsBar(
    words: List<String>,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (words.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { word ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { onWordClick(word) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    word,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
