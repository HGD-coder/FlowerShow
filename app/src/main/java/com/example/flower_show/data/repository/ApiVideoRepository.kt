package com.example.flower_show.data.repository

import com.example.flower_show.config.NetworkConfig
import com.example.flower_show.data.remote.EventBatchRequestDto
import com.example.flower_show.data.remote.FeedPageRequestDto
import com.example.flower_show.data.remote.GuessPageRequestDto
import com.example.flower_show.data.remote.RecommendationApi
import com.example.flower_show.data.remote.SearchVideosRequestDto
import com.example.flower_show.data.remote.SocialApi
import com.example.flower_show.data.remote.toModel
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.ContentComment
import com.example.flower_show.model.ContentStats
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.GuessPage
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.RecommendationEventResult
import com.example.flower_show.model.RecommendedPage
import com.example.flower_show.model.Result
import com.example.flower_show.model.SearchPage
import okhttp3.OkHttpClient
import java.util.UUID

class ApiVideoRepository(
    baseUrl: String = defaultBaseUrl(),
    client: OkHttpClient = OkHttpClient(),
    installId: String = UUID.randomUUID().toString(),
    private val api: SocialApi = SocialApi(client.withInstallId(installId), baseUrl),
    private val recommendationApi: RecommendationApi =
        RecommendationApi(client.withInstallId(installId), baseUrl),
) : IVideoRepository {

    override fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>> =
        networkResult {
            api.feed(page, pageSize).mapNotNull { it.toModel() }
        }

    override fun loadRecommendedFeed(
        cursor: String?,
        serveSessionId: String?,
        pageSize: Int,
        refresh: Boolean,
    ): Result<RecommendedPage> = networkResult {
        recommendationApi.feedPages(
            FeedPageRequestDto(
                clientRequestId = UUID.randomUUID().toString(),
                serveSessionId = serveSessionId,
                cursor = cursor,
                limit = pageSize,
                refresh = refresh,
            ),
        ).toModel()
    }

    override fun search(keyword: String): Result<List<CardItem>> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return when (val result = searchVideos(trimmed, cursor = null, pageSize = 20)) {
            is Result.Success -> Result.success(result.data.items.map { it.card })
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override fun searchVideos(
        keyword: String,
        cursor: String?,
        pageSize: Int,
    ): Result<SearchPage> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return Result.error("Search query must not be blank")
        return networkResult {
            recommendationApi.searchVideos(
                SearchVideosRequestDto(
                    clientRequestId = UUID.randomUUID().toString(),
                    query = trimmed,
                    cursor = cursor,
                    limit = pageSize,
                ),
            ).toModel()
        }
    }

    override fun loadSearchGuesses(
        cursor: String?,
        serveSessionId: String?,
        pageSize: Int,
        refresh: Boolean,
    ): Result<GuessPage> = networkResult {
        recommendationApi.searchGuesses(
            GuessPageRequestDto(
                clientRequestId = UUID.randomUUID().toString(),
                serveSessionId = serveSessionId,
                cursor = cursor,
                limit = pageSize,
                refresh = refresh,
            ),
        ).toModel()
    }

    override fun reportEvents(
        events: List<RecommendationEvent>,
    ): Result<List<RecommendationEventResult>> {
        if (events.isEmpty()) return Result.success(emptyList())
        return networkResult {
            recommendationApi.reportEvents(EventBatchRequestDto(events)).toModel()
        }
    }

    override fun loadFollowingFeed(
        cursor: String?,
        pageSize: Int,
    ): Result<CursorPage<CardItem>> = networkResult {
        val response = api.followingFeed(cursor, pageSize)
        CursorPage(
            items = response.items.orEmpty().mapNotNull { it.toModel() },
            nextCursor = response.nextCursor,
            hasMore = (response.hasMore ?: false) && !response.nextCursor.isNullOrBlank(),
        )
    }

    override fun setLiked(
        contentId: String,
        userId: String,
        liked: Boolean,
    ): Result<ContentStats> = networkResult {
        api.setLiked(contentId, userId, liked).stats?.toModel()
            ?: throw IllegalStateException("Missing content stats")
    }

    override fun setFavorited(
        contentId: String,
        userId: String,
        favorited: Boolean,
    ): Result<ContentStats> = networkResult {
        api.setFavorited(contentId, userId, favorited).stats?.toModel()
            ?: throw IllegalStateException("Missing content stats")
    }

    override fun loadComments(contentId: String): Result<List<ContentComment>> =
        networkResult {
            api.comments(contentId).map { it.toModel() }
        }

    override fun createComment(
        contentId: String,
        userId: String,
        body: String,
    ): Result<ContentComment> = networkResult {
        api.createComment(contentId, userId, body).toModel()
    }

    private fun <T> networkResult(action: () -> T): Result<T> = try {
        Result.success(action())
    } catch (e: Exception) {
        Result.error("Network request failed: ${e.message}")
    }

    companion object {
        fun defaultBaseUrl(): String = NetworkConfig.apiBaseUrl
    }
}

private fun OkHttpClient.withInstallId(installId: String): OkHttpClient {
    require(installId.isNotBlank()) { "installId must not be blank" }
    return newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("X-Install-Id", installId)
                    .build(),
            )
        }
        .build()
}
