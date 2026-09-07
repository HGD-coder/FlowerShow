package com.example.flower_show.data.remote

import com.example.flower_show.model.DeliveredCard
import com.example.flower_show.model.DeliveryContext
import com.example.flower_show.model.GuessPage
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.RecommendationEventResult
import com.example.flower_show.model.RecommendedPage
import com.example.flower_show.model.SearchGuess
import com.example.flower_show.model.SearchPage

data class ApiEnvelopeDto<T>(
    val requestId: String? = null,
    val traceId: String? = null,
    val serverTimeMs: Long? = null,
    val data: T? = null,
    val error: ApiErrorDto? = null,
)

data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    val details: Map<String, Any?>? = null,
)

data class FeedPageRequestDto(
    val clientRequestId: String,
    val serveSessionId: String? = null,
    val cursor: String? = null,
    val limit: Int,
    val scene: String? = null,
    val refresh: Boolean,
)

data class GuessPageRequestDto(
    val clientRequestId: String,
    val serveSessionId: String? = null,
    val cursor: String? = null,
    val limit: Int,
    val refresh: Boolean,
)

data class SearchVideosRequestDto(
    val clientRequestId: String,
    val query: String,
    val cursor: String? = null,
    val limit: Int,
)

data class FeedPageDataDto(
    val serveSessionId: String? = null,
    val items: List<RankedCardItemDto>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
    val serveMode: String? = null,
    val algoVersion: String? = null,
    val policyVersion: String? = null,
    val expiresAtMs: Long? = null,
)

data class SearchPageDataDto(
    val serveSessionId: String? = null,
    val items: List<RankedCardItemDto>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
    val algoVersion: String? = null,
    val policyVersion: String? = null,
    val expiresAtMs: Long? = null,
)

data class GuessPageDataDto(
    val serveSessionId: String? = null,
    val items: List<RankedSuggestionDto>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
    val algoVersion: String? = null,
    val policyVersion: String? = null,
    val expiresAtMs: Long? = null,
)

data class RankedCardItemDto(
    val id: String? = null,
    val rank: Int? = null,
    val source: String? = null,
    val deliveryType: String? = null,
    val payload: CardItemDto? = null,
    val exposureToken: String? = null,
    val relatedSearch: String? = null,
)

data class RankedSuggestionDto(
    val id: String? = null,
    val text: String? = null,
    val rank: Int? = null,
    val source: String? = null,
    val exposureToken: String? = null,
)

data class EventBatchRequestDto(val events: List<RecommendationEvent>)

data class EventBatchDataDto(val results: List<EventResultDto>? = null)

data class EventResultDto(
    val eventId: String? = null,
    val status: String? = null,
    val code: String? = null,
    val message: String? = null,
)

internal fun FeedPageDataDto.toModel(): RecommendedPage {
    val pageItems = requireNotNull(items) { "Missing feed items" }
        .mapNotNull(RankedCardItemDto::toModelOrNull)
    val more = requireNotNull(hasMore) { "Missing feed hasMore" }
    validateCursor(more, nextCursor)
    return RecommendedPage(
        serveSessionId = serveSessionId.requireWireValue("feed serveSessionId"),
        items = pageItems,
        nextCursor = nextCursor?.takeIf(String::isNotBlank),
        hasMore = more,
        serveMode = serveMode.requireWireValue("feed serveMode"),
        algoVersion = algoVersion.requireWireValue("feed algoVersion"),
        policyVersion = policyVersion.requireWireValue("feed policyVersion"),
        expiresAtMs = requireNotNull(expiresAtMs) { "Missing feed expiresAtMs" },
    )
}

internal fun SearchPageDataDto.toModel(): SearchPage {
    val pageItems = requireNotNull(items) { "Missing search items" }
        .mapNotNull(RankedCardItemDto::toModelOrNull)
    val more = requireNotNull(hasMore) { "Missing search hasMore" }
    validateCursor(more, nextCursor)
    return SearchPage(
        serveSessionId = serveSessionId.requireWireValue("search serveSessionId"),
        items = pageItems,
        nextCursor = nextCursor?.takeIf(String::isNotBlank),
        hasMore = more,
        algoVersion = algoVersion.requireWireValue("search algoVersion"),
        policyVersion = policyVersion.requireWireValue("search policyVersion"),
        expiresAtMs = requireNotNull(expiresAtMs) { "Missing search expiresAtMs" },
    )
}

internal fun GuessPageDataDto.toModel(): GuessPage {
    val pageItems = requireNotNull(items) { "Missing guess items" }.map { item ->
        SearchGuess(
            id = item.id.requireWireValue("suggestion id"),
            text = item.text.requireWireValue("suggestion text"),
            rank = requireNotNull(item.rank) { "Missing suggestion rank" },
            source = item.source.requireWireValue("suggestion source"),
            exposureToken = item.exposureToken.requireWireValue("suggestion exposureToken"),
        )
    }
    val more = requireNotNull(hasMore) { "Missing guess hasMore" }
    validateCursor(more, nextCursor)
    return GuessPage(
        serveSessionId = serveSessionId.requireWireValue("guess serveSessionId"),
        items = pageItems,
        nextCursor = nextCursor?.takeIf(String::isNotBlank),
        hasMore = more,
        algoVersion = algoVersion.requireWireValue("guess algoVersion"),
        policyVersion = policyVersion.requireWireValue("guess policyVersion"),
        expiresAtMs = requireNotNull(expiresAtMs) { "Missing guess expiresAtMs" },
    )
}

internal fun EventBatchDataDto.toModel(): List<RecommendationEventResult> =
    requireNotNull(results) { "Missing event results" }.map { result ->
        RecommendationEventResult(
            eventId = result.eventId.requireWireValue("event result eventId"),
            status = result.status.requireWireValue("event result status"),
            code = result.code,
            message = result.message,
        )
    }

/**
 * 单条推荐项转模型。
 *
 * 返回 null 表示该条应被过滤（例如广告/推广等非 organic 投放位），
 * 而不是让整页解析失败——单条坏数据不应该毒化整个 Feed/搜索页。
 */
private fun RankedCardItemDto.toModelOrNull(): DeliveredCard? {
    val resolvedDeliveryType = deliveryType.requireWireValue("ranked item deliveryType")
    if (resolvedDeliveryType != ORGANIC_DELIVERY_TYPE) return null
    val card = requireNotNull(payload) { "Missing ranked item payload" }.toModel()
        ?: throw IllegalArgumentException("Unsupported or invalid ranked item payload")
    val contentId = payload.id.requireWireValue("ranked item payload id")
    return DeliveredCard(
        card = card,
        delivery = DeliveryContext(
            id = id.requireWireValue("ranked item id"),
            contentId = contentId,
            rank = requireNotNull(rank) { "Missing ranked item rank" },
            source = source.requireWireValue("ranked item source"),
            deliveryType = resolvedDeliveryType,
            exposureToken = exposureToken.requireWireValue("ranked item exposureToken"),
            relatedSearch = relatedSearch?.trim()?.takeIf(String::isNotEmpty),
        ),
    )
}

private fun String?.requireWireValue(name: String): String =
    requireNotNull(this) { "Missing $name" }.also { require(it.isNotBlank()) { "Blank $name" } }

private fun validateCursor(hasMore: Boolean, nextCursor: String?) {
    if (hasMore) require(!nextCursor.isNullOrBlank()) { "Missing nextCursor while hasMore is true" }
}

private const val ORGANIC_DELIVERY_TYPE = "organic"
