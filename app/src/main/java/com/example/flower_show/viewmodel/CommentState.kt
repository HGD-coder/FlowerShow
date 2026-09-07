package com.example.flower_show.viewmodel

import androidx.compose.runtime.Immutable
import com.example.flower_show.model.VideoComment

@Immutable
data class CommentState(
    val visible: Boolean = false,
    val videoId: String? = null,
    val totalCount: Int = 0,
    val comments: List<VideoComment> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
)
