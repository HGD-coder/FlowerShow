package com.example.flower_show.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.flower_show.ai.OnDeviceVectorSearchEngine
import com.example.flower_show.ai.VectorSearchEngine
import com.example.flower_show.data.local.AssetJsonLoader
import com.example.flower_show.model.*
import com.example.flower_show.util.MetricsCollector
import kotlin.math.log10

/**
 * FakeVideoRepository - Local data implementation (Singleton + Cache)
 *
 * Data source: assets/video_data.json or assets/video_data.jsonl.
 * Implements IVideoRepository for DIP compliance.
 */
class FakeVideoRepository private constructor(
    private val context: Context?,
    private val testVideos: List<VideoItem>? = null,
    private val testFeedItems: List<CardItem>? = null,
    private val searchMatcher: SearchMatcher = HybridSearchMatcher(),
    private val vectorSearchEngine: VectorSearchEngine? = context?.applicationContext?.let {
        OnDeviceVectorSearchEngine(it)
    },
) : IVideoRepository {

    private var cachedVideos: List<VideoItem>? = null
    private var cachedImageCards: List<CardItem>? = null
    private var cachedFeedItems: List<CardItem>? = null

    companion object {
        @Volatile private var instance: FakeVideoRepository? = null

        private const val TEXT_SEARCH_WEIGHT = 0.55f
        private const val VECTOR_SEARCH_WEIGHT = 0.40f
        private const val POPULARITY_SEARCH_WEIGHT = 0.05f
        private const val MIN_VECTOR_ONLY_SEARCH_SCORE = 0.55f
        private const val MAX_VECTOR_ONLY_CANDIDATES = 20

        fun getInstance(context: Context?): FakeVideoRepository {
            return instance ?: synchronized(this) {
                instance ?: FakeVideoRepository(context?.applicationContext).also { instance = it }
            }
        }

        @VisibleForTesting
        fun withVideos(
            videos: List<VideoItem>,
            matcher: SearchMatcher = HybridSearchMatcher(),
            vectorSearchEngine: VectorSearchEngine? = null,
        ): FakeVideoRepository {
            return FakeVideoRepository(
                context = null,
                testVideos = videos,
                searchMatcher = matcher,
                vectorSearchEngine = vectorSearchEngine,
            )
        }

        @VisibleForTesting
        fun withFeedItems(
            feedItems: List<CardItem>,
            matcher: SearchMatcher = HybridSearchMatcher(),
            vectorSearchEngine: VectorSearchEngine? = null,
        ): FakeVideoRepository {
            return FakeVideoRepository(
                context = null,
                testVideos = feedItems.filterIsInstance<VideoItem>(),
                testFeedItems = feedItems,
                searchMatcher = matcher,
                vectorSearchEngine = vectorSearchEngine,
            )
        }
    }

    override fun refreshFeedSession() {
        cachedFeedItems = null
    }

    override fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>> {
        return try {
            Result.success(paginate(getSessionFeedItems(), page, pageSize))
        } catch (e: Exception) {
            Result.error("加载失败: ${e.message}")
        }
    }

    override fun search(keyword: String): Result<List<CardItem>> {
        return try {
            val videos = getCachedVideos()
            val imageCards = getSearchableImageCards()
            val lower = keyword.lowercase().trim()
            if (lower.isEmpty()) return Result.success(emptyList())

            val startMs = System.currentTimeMillis()
            val strategyName = searchMatcher.javaClass.simpleName

            val vectorScores = vectorSearchEngine?.score(lower, videos).orEmpty()
            val vectorOnlyCandidateIds = vectorScores.entries
                .asSequence()
                .filter { it.value >= MIN_VECTOR_ONLY_SEARCH_SCORE }
                .sortedByDescending { it.value }
                .take(MAX_VECTOR_ONLY_CANDIDATES)
                .map { it.key }
                .toSet()
            MetricsCollector.recordLabel(
                "vector_search_state",
                if (vectorScores.isEmpty()) "disabled_or_empty" else "enabled",
            )

            val scoredVideos: List<Pair<CardItem, Float>> = videos.mapNotNull { v ->
                val textScore = searchMatcher.score(v, lower)
                val vectorScore = vectorScores[v.id] ?: 0f
                if (textScore <= 0f && v.id !in vectorOnlyCandidateIds) return@mapNotNull null
                (v as CardItem) to combineSearchScore(
                    textScore = textScore,
                    vectorScore = vectorScore,
                    video = v,
                    hasVectorScores = vectorScores.isNotEmpty(),
                )
            }
            val scoredImageCards = imageCards.mapNotNull { card ->
                val score = imageCardSearchScore(card, lower)
                if (score > 0f) card to score else null
            }
            val results = (scoredVideos + scoredImageCards)
                .sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { it.feedDeduplicateKey() }
            val timeMs = System.currentTimeMillis() - startMs

            MetricsCollector.record("search_query|strategy=${strategyName.lowercase()}", timeMs)
            MetricsCollector.record("search_result_count", results.size.toLong())
            Result.success(results)
        } catch (e: Exception) {
            Result.error("搜索失败: ${e.message}")
        }
    }

    private fun getCachedVideos(): List<VideoItem> {
        if (cachedVideos == null) {
            cachedVideos = (testVideos ?: context?.let { AssetJsonLoader.loadVideos(it) } ?: emptyList())
                .deduplicateVideos()
        }
        return cachedVideos ?: emptyList()
    }

    private fun getCachedImageCards(): List<CardItem> {
        if (cachedImageCards == null) {
            cachedImageCards = context?.let { AssetJsonLoader.loadImageCards(it) }.orEmpty()
        }
        return cachedImageCards ?: emptyList()
    }

    private fun getSearchableImageCards(): List<CardItem> {
        return testFeedItems?.filter { it is ImageCardItem || it is AlbumCardItem }
            ?: getCachedImageCards()
    }

    private fun getSessionFeedItems(): List<CardItem> {
        val cached = cachedFeedItems
        if (cached != null) return cached

        val sourceItems = testFeedItems ?: (getCachedVideos() + getCachedImageCards())
        val feedItems = sourceItems.deduplicateFeedItems().shuffled()

        cachedFeedItems = feedItems
        return feedItems
    }

    private fun paginate(items: List<CardItem>, page: Int, pageSize: Int): List<CardItem> {
        val from = (page - 1) * pageSize
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + pageSize, items.size))
    }

    private fun combineSearchScore(
        textScore: Float,
        vectorScore: Float,
        video: VideoItem,
        hasVectorScores: Boolean,
    ): Float {
        if (!hasVectorScores) return textScore
        val popularityScore = popularityScore(video)
        return (
            textScore * TEXT_SEARCH_WEIGHT +
                vectorScore * VECTOR_SEARCH_WEIGHT +
                popularityScore * POPULARITY_SEARCH_WEIGHT
            ).coerceIn(0f, 1f)
    }

    private fun popularityScore(video: VideoItem): Float {
        val raw = video.likes.toDouble() +
            video.collections * 1.2 +
            video.comments * 1.5 +
            video.shares * 1.5
        return (log10(raw.coerceAtLeast(0.0) + 1.0) / 7.0).toFloat().coerceIn(0f, 1f)
    }

    private fun imageCardSearchScore(card: CardItem, query: String): Float {
        if (query.isBlank()) return 0f
        return when (card) {
            is ImageCardItem -> simpleTextSearchScore(
                query = query,
                title = card.title,
                author = card.author,
                tags = emptyList(),
                recommendWords = emptyList(),
            )
            is AlbumCardItem -> simpleTextSearchScore(
                query = query,
                title = card.title,
                author = card.author,
                tags = card.tags,
                recommendWords = card.recommendWords,
            )
            else -> 0f
        }
    }

    private fun simpleTextSearchScore(
        query: String,
        title: String,
        author: String,
        tags: List<String>,
        recommendWords: List<String>,
    ): Float {
        val lowerTitle = title.lowercase()
        var score = 0f
        if (lowerTitle == query) score += 1.0f
        else if (lowerTitle.contains(query)) score += 0.85f
        if (author.lowercase().contains(query)) score += 0.25f
        score += tags.count { it.lowercase().contains(query) } * 0.45f
        score += recommendWords.count { it.lowercase().contains(query) } * 0.25f
        return if (score >= 0.25f) score.coerceAtMost(1f) else 0f
    }

    private fun List<VideoItem>.deduplicateVideos(): List<VideoItem> {
        return distinctBy { video ->
            video.id.ifBlank { "${video.videoUrl}|${video.title}" }
        }
    }

    private fun List<CardItem>.deduplicateFeedItems(): List<CardItem> {
        return distinctBy { it.feedDeduplicateKey() }
    }

    private fun CardItem.feedDeduplicateKey(): String {
        return when (this) {
            is VideoItem -> "video:${id.ifBlank { "$videoUrl|$title" }}"
            is ImageCardItem -> "image:${id.ifBlank { imageUrl }}"
            is AlbumCardItem -> {
                val fallback = slides.joinToString("|") { it.mediaUrl }
                "album:${id.ifBlank { fallback }}"
            }
            CardItem.TypeVideo -> "type_video"
            CardItem.TypeImage -> "type_image"
            CardItem.TypeAlbum -> "type_album"
        }
    }

}
