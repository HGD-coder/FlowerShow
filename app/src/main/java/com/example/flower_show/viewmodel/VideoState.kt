package com.example.flower_show.viewmodel

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.QualityMode
import com.example.flower_show.model.VideoQuality

data class VideoState(
    val items: List<CardItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val isPlayerReady: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val targetVideoId: String? = null,

    // ── Quality / 画质 ──
    val qualityMode: QualityMode = QualityMode.Auto,
    val currentQualityName: String? = null,
    val availableQualities: List<VideoQuality> = emptyList(),
)
