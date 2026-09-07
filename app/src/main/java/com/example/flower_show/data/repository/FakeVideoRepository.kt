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
 * 本地 assets 数据版视频仓库。
 *
 * 数据来自打包在 APK 里的本地文件，不需要后端：
 * - 视频数据来自 assets/video_data.json 或 assets/video_data.jsonl。
 * - 图片/图集数据来自 assets/image_data.json 或 assets/image_data.jsonl。
 * - AssetJsonLoader 负责把 JSON 解析成 VideoItem / ImageCardItem / AlbumCardItem。
 *
 * 生产环境由 RepositoryFactory 返回 ApiVideoRepository（走认证网络请求）；
 * 本实现主要供单元测试与离线数据调试使用。
 *
 * 你读数据主流程时，重点看：
 * - loadFeed：首页分页从这里拿数据。
 * - search：搜索页从这里拿结果。
 * - getSessionFeedItems：把视频、图片、图集合成首页信息流。
 * - paginate：根据 page/pageSize 切出当前页。
 */
class FakeVideoRepository private constructor(
    /**
     * Android Context，用来读取 assets。
     *
     * 测试时可以传 null，然后通过 testVideos/testFeedItems 提供数据。
     */
    private val context: Context?,

    /**
     * 单元测试专用的视频列表。
     */
    private val testVideos: List<VideoItem>? = null,

    /**
     * 单元测试专用的完整信息流列表。
     */
    private val testFeedItems: List<CardItem>? = null,

    /**
     * 文本搜索匹配器。
     *
     * HybridSearchMatcher 会根据标题、作者、标签、推荐词等计算文本相关度。
     */
    private val searchMatcher: SearchMatcher = HybridSearchMatcher(),

    /**
     * 向量搜索引擎。
     *
     * 如果本地 assets 里有向量索引，就可以把关键词和视频向量做相似度匹配。
     * 第一遍读数据层时，只需要知道它是“增强搜索相关度”的可选能力。
     */
    private val vectorSearchEngine: VectorSearchEngine? = context?.applicationContext?.let {
        OnDeviceVectorSearchEngine(it)
    },
) : IVideoRepository {

    /**
     * 缓存解析后的纯视频列表，避免每次 loadFeed/search 都重新读 JSON。
     */
    private var cachedVideos: List<VideoItem>? = null

    /**
     * 缓存解析后的图片/图集卡片。
     */
    private var cachedImageCards: List<CardItem>? = null

    /**
     * 缓存当前首页会话的信息流顺序。
     *
     * refreshFeedSession() 会清空它；
     * 下一次 getSessionFeedItems() 会重新打乱并缓存。
     */
    private var cachedFeedItems: List<CardItem>? = null

    companion object {
        /**
         * 单例实例。
         *
         * @Volatile 保证多线程下读取 instance 时能看到最新值。
         */
        @Volatile private var instance: FakeVideoRepository? = null

        // 搜索总分权重：文本匹配 55%，向量相似度 40%，热度 5%。
        private const val TEXT_SEARCH_WEIGHT = 0.55f
        private const val VECTOR_SEARCH_WEIGHT = 0.40f
        private const val POPULARITY_SEARCH_WEIGHT = 0.05f

        // 只靠向量搜索也能入围的最低分，以及最多保留多少个候选。
        private const val MIN_VECTOR_ONLY_SEARCH_SCORE = 0.55f
        private const val MAX_VECTOR_ONLY_CANDIDATES = 20

        /**
         * 获取仓库单例。
         *
         * 首页 ViewModel、搜索仓库都会拿同一个 FakeVideoRepository，
         * 这样缓存可以复用，避免重复解析 assets。
         */
        fun getInstance(context: Context?): FakeVideoRepository {
            return instance ?: synchronized(this) {
                instance ?: FakeVideoRepository(context?.applicationContext).also { instance = it }
            }
        }

        /**
         * 测试辅助方法：只传视频列表创建仓库。
         *
         * 生产代码不会走这里。
         */
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

        /**
         * 测试辅助方法：传完整 CardItem 信息流创建仓库。
         *
         * 适合测试视频、图片、图集混排的情况。
         */
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

    /**
     * 刷新首页会话。
     *
     * 当前做法很简单：清空 cachedFeedItems。
     * 下次 loadFeed 时，getSessionFeedItems() 会重新把数据打乱成新的首页顺序。
     *
     * 与 getSessionFeedItems() 共用同一把锁：刷新会等正在进行的会话构建完成后再清空，
     * 避免并发时旧会话顺序在刷新后被重新写回缓存（"新会话"悄悄保留旧顺序）。
     */
    @Synchronized
    override fun refreshFeedSession() {
        cachedFeedItems = null
    }

    /**
     * 分页加载首页信息流。
     *
     * 这是 VideoViewModel.loadFirstPage/loadNextPage 最终调用的方法。
     *
     * 流程：
     * 1. getSessionFeedItems() 拿到当前首页会话的完整列表。
     * 2. paginate(...) 根据 page/pageSize 切出一页。
     * 3. 用 Result.success 包起来返回。
     */
    override fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>> {
        return try {
            Result.success(paginate(getSessionFeedItems(), page, pageSize))
        } catch (e: Exception) {
            Result.error("加载失败: ${e.message}")
        }
    }

    /**
     * 搜索视频、图片、图集。
     *
     * 搜索大体分三步：
     * 1. 准备数据：视频列表 + 图片/图集列表。
     * 2. 给每条内容算相关度分数。
     * 3. 按分数降序排序，并去重。
     *
     * 视频搜索会综合：
     * - 文本匹配分
     * - 向量相似度分
     * - 热度分
     *
     * 图片/图集当前只做简单文本匹配。
     */
    override fun search(keyword: String): Result<List<CardItem>> {
        return try {
            val videos = getCachedVideos()
            val imageCards = getSearchableImageCards()

            // 统一转小写、去空格，避免大小写和首尾空格影响搜索。
            val lower = keyword.lowercase().trim()
            if (lower.isEmpty()) return Result.success(emptyList())

            val startMs = System.currentTimeMillis()
            val strategyName = searchMatcher.javaClass.simpleName

            // 向量搜索分数：key 是视频 id，value 是相似度分数。
            val vectorScores = vectorSearchEngine?.score(lower, videos).orEmpty()

            // 如果某些视频文本匹配不到，但向量相似度足够高，也允许它们进入候选。
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

            // 给视频内容打分。
            val scoredVideos: List<Pair<CardItem, Float>> = videos.mapNotNull { v ->
                val textScore = searchMatcher.score(v, lower)
                val vectorScore = vectorScores[v.id] ?: 0f

                // 文本不匹配、向量也不够高的内容，不进入结果。
                if (textScore <= 0f && v.id !in vectorOnlyCandidateIds) return@mapNotNull null
                (v as CardItem) to combineSearchScore(
                    textScore = textScore,
                    vectorScore = vectorScore,
                    video = v,
                    hasVectorScores = vectorScores.isNotEmpty(),
                )
            }

            // 给图片/图集内容打分。
            val scoredImageCards = imageCards.mapNotNull { card ->
                val score = imageCardSearchScore(card, lower)
                if (score > 0f) card to score else null
            }

            // 合并、排序、去重后得到最终搜索结果。
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

    /**
     * 获取视频缓存。
     *
     * 第一次调用时：
     * - 如果是测试仓库，用 testVideos。
     * - 否则从 assets 读取并解析视频 JSON。
     *
     * 后续调用直接返回 cachedVideos。
     *
     * loadFeed/search 可能从不同 IO 线程并发进入：加锁串行化
     * "判空-解析-写缓存"，避免并发重复解析 assets。
     */
    @Synchronized
    private fun getCachedVideos(): List<VideoItem> {
        if (cachedVideos == null) {
            cachedVideos = (testVideos ?: context?.let { AssetJsonLoader.loadVideos(it) } ?: emptyList())
                .deduplicateVideos()
        }
        return cachedVideos ?: emptyList()
    }

    /**
     * 获取图片/图集缓存。
     */
    @Synchronized
    private fun getCachedImageCards(): List<CardItem> {
        if (cachedImageCards == null) {
            cachedImageCards = context?.let { AssetJsonLoader.loadImageCards(it) }.orEmpty()
        }
        return cachedImageCards ?: emptyList()
    }

    /**
     * 获取可以参与搜索的图片/图集内容。
     *
     * 测试时优先从 testFeedItems 里筛选；
     * 正常运行时从 assets 图片数据缓存里拿。
     */
    private fun getSearchableImageCards(): List<CardItem> {
        return testFeedItems?.filter { it is ImageCardItem || it is AlbumCardItem }
            ?: getCachedImageCards()
    }

    /**
     * 获取当前首页会话的完整信息流列表。
     *
     * 当前策略：
     * - 视频 + 图片/图集 合并。
     * - 去重。
     * - shuffled() 打乱顺序。
     * - 缓存起来，保证同一次会话翻页时顺序稳定。
     */
    @Synchronized
    private fun getSessionFeedItems(): List<CardItem> {
        val cached = cachedFeedItems
        if (cached != null) return cached

        val sourceItems = testFeedItems ?: (getCachedVideos() + getCachedImageCards())
        val feedItems = sourceItems.deduplicateFeedItems().shuffled()

        cachedFeedItems = feedItems
        return feedItems
    }

    /**
     * 根据页码切出一页数据。
     *
     * page 从 1 开始：
     * - page=1, pageSize=10 -> 取 0..9
     * - page=2, pageSize=10 -> 取 10..19
     */
    private fun paginate(items: List<CardItem>, page: Int, pageSize: Int): List<CardItem> {
        val from = (page - 1) * pageSize
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + pageSize, items.size))
    }

    /**
     * 合并搜索分数。
     *
     * 如果没有向量分数，就只用文本分数。
     * 如果有向量分数，就按权重综合：
     * 文本 55% + 向量 40% + 热度 5%。
     */
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

    /**
     * 计算视频热度分。
     *
     * 点赞、收藏、评论、分享会被综合成一个 0..1 的分数。
     * log10 用来压缩大数字，避免点赞特别高的视频完全碾压其他结果。
     */
    private fun popularityScore(video: VideoItem): Float {
        val raw = video.likes.toDouble() +
            video.collections * 1.2 +
            video.comments * 1.5 +
            video.shares * 1.5
        return (log10(raw.coerceAtLeast(0.0) + 1.0) / 7.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 图片/图集的搜索分数。
     *
     * 当前只做简单文本匹配，没有向量搜索。
     */
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

    /**
     * 简单文本搜索打分。
     *
     * 匹配越强，分数越高：
     * - 标题完全等于关键词：最高
     * - 标题包含关键词：较高
     * - 作者、标签、推荐词命中：加分
     */
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

    /**
     * 视频去重。
     *
     * 优先按 id 去重；如果 id 为空，就用 videoUrl + title 兜底。
     */
    private fun List<VideoItem>.deduplicateVideos(): List<VideoItem> {
        return distinctBy { video ->
            video.id.ifBlank { "${video.videoUrl}|${video.title}" }
        }
    }

    /**
     * 首页卡片去重。
     */
    private fun List<CardItem>.deduplicateFeedItems(): List<CardItem> {
        return distinctBy { it.feedDeduplicateKey() }
    }

    /**
     * 生成卡片去重 key。
     *
     * 不同类型加不同前缀，避免视频 id 和图片 id 恰好相同时互相误删。
     */
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
