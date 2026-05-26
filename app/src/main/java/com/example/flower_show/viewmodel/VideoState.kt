package com.example.flower_show.viewmodel

import com.example.flower_show.model.CardItem

/**
 * VideoState — Single source of truth for the video feed / 视频流全部状态
 *
 * MVI pattern: ONE object holds all state. ViewModel emits a new VideoState
 * on every change. The UI observes this single flow.
 * MVI 模式：一个对象持有全部状态。每次变化 ViewModel 发一个新的 VideoState。
 */
data class VideoState(
    val items: List<CardItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val isPlayerReady: Boolean = false,
    val error: String? = null,
)
