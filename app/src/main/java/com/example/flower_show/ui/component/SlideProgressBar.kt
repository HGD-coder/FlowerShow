package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SlideProgressBar - Horizontal progress indicator for album cards
 * Draws N bars with the current one highlighted.
 */
@Composable
fun SlideProgressBar(
    slideCount: Int,
    currentSlide: Int,
    modifier: Modifier = Modifier,
) {
    if (slideCount <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(slideCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index == currentSlide) Color.White else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}
