package com.example.flower_show.data.repository

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

/**
 * 视频/首页信息流的数据仓库接口。
 *
 * 这里是数据层和 ViewModel 之间的“合同”：
 * ViewModel 只知道自己可以调用这些方法，但不关心数据到底来自哪里。
 *
 * 为什么要先写接口？
 * - ViewModel 依赖接口，而不是依赖具体类，后续可以把本地 JSON 数据换成网络接口。
 * - 单元测试时也可以传入假的 Repository，专门测试 ViewModel 逻辑。
 */
interface IVideoRepository {
    /**
     * 刷新首页信息流会话。
     *
     * 仅为旧实现保留；服务端 Recommended 会话由 loadRecommendedFeed 的
     * refresh/serveSessionId/cursor 合同控制。
     */
    fun refreshFeedSession() = Unit

    /**
     * 分页加载首页信息流。
     *
     * page：第几页，从 1 开始。
     * pageSize：每页多少条。
     *
     * 返回的是 Result<List<CardItem>>：
     * - Success：拿到一页卡片数据
     * - Error：加载失败
     * - Loading：预留的加载中状态
     */
    fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>>

    fun loadRecommendedFeed(
        cursor: String?,
        serveSessionId: String?,
        pageSize: Int,
        refresh: Boolean,
    ): Result<RecommendedPage> =
        Result.error("Recommended feed is not supported by this repository")

    /**
     * 搜索信息流内容。
     *
     * 搜索结果也是 List<CardItem>，所以结果里可以同时出现视频、图片、图集。
     */
    fun search(keyword: String): Result<List<CardItem>>

    fun searchVideos(
        keyword: String,
        cursor: String?,
        pageSize: Int,
    ): Result<SearchPage> =
        Result.error("Server search is not supported by this repository")

    fun loadSearchGuesses(
        cursor: String?,
        serveSessionId: String?,
        pageSize: Int,
        refresh: Boolean,
    ): Result<GuessPage> =
        Result.error("Search guesses are not supported by this repository")

    fun reportEvents(events: List<RecommendationEvent>): Result<List<RecommendationEventResult>> =
        Result.error("Recommendation events are not supported by this repository")

    /** Cursor-based feed containing only authors followed by the current user. */
    fun loadFollowingFeed(cursor: String?, pageSize: Int): Result<CursorPage<CardItem>> =
        Result.error("Following feed is not supported by this repository")

    /** POST adds an interaction; DELETE removes it. */
    fun setLiked(contentId: String, userId: String, liked: Boolean): Result<ContentStats> =
        Result.error("Likes are not supported by this repository")

    fun setFavorited(contentId: String, userId: String, favorited: Boolean): Result<ContentStats> =
        Result.error("Favorites are not supported by this repository")

    fun loadComments(contentId: String): Result<List<ContentComment>> =
        Result.error("Comments are not supported by this repository")

    fun createComment(contentId: String, userId: String, body: String): Result<ContentComment> =
        Result.error("Comments are not supported by this repository")
}
