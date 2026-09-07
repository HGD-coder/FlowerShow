package com.example.flower_show.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoComment(
    val id: String,
    val author: String,
    val content: String,
    val timeLabel: String,
    val avatarUrl: String = "",
    val isMine: Boolean = false,
)
