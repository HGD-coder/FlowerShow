package com.example.flower_show.model

import androidx.compose.runtime.Immutable

@Immutable
data class DeliveryContext(
    val id: String,
    val contentId: String,
    val rank: Int,
    val source: String,
    val deliveryType: String,
    val exposureToken: String,
    val relatedSearch: String? = null,
)

@Immutable
data class DeliveredCard(
    val card: CardItem,
    val delivery: DeliveryContext,
)

@Immutable
data class RecommendedPage(
    val serveSessionId: String,
    val items: List<DeliveredCard>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val serveMode: String,
    val algoVersion: String,
    val policyVersion: String,
    val expiresAtMs: Long,
)

@Immutable
data class SearchGuess(
    val id: String,
    val text: String,
    val rank: Int,
    val source: String,
    val exposureToken: String,
)

@Immutable
data class GuessPage(
    val serveSessionId: String,
    val items: List<SearchGuess>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val algoVersion: String,
    val policyVersion: String,
    val expiresAtMs: Long,
)

@Immutable
data class SearchPage(
    val serveSessionId: String,
    val items: List<DeliveredCard>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val algoVersion: String,
    val policyVersion: String,
    val expiresAtMs: Long,
)

@Immutable
data class RecommendationEvent(
    val eventId: String,
    val type: String,
    val occurredAtMs: Long,
    val exposureToken: String? = null,
    val contentId: String? = null,
    val suggestionId: String? = null,
    val query: String? = null,
    val watchMs: Long? = null,
    val playbackId: String? = null,
    val sequence: Long? = null,
)

@Immutable
data class RecommendationEventResult(
    val eventId: String,
    val status: String,
    val code: String? = null,
    val message: String? = null,
)
