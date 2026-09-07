package com.example.flower_show.data.repository

import android.content.Context
import com.example.flower_show.data.local.SearchHistoryManager
import com.example.flower_show.model.GuessPage
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.RecommendationEventResult
import com.example.flower_show.model.Result
import com.example.flower_show.model.SearchPage

/**
 * 搜索页仓库：仅历史记录保存在本地。
 *
 * 搜索结果、猜你想搜和事件上报全部委托给网络 IVideoRepository；
 * 本类不生成、旋转或降级任何服务端结果。
 */
class LocalSearchRepository(
    context: Context,
    private val videoRepo: IVideoRepository = RepositoryFactory.getVideoRepository(context),
) : ISearchRepository {

    private val historyManager = SearchHistoryManager(context)

    override fun search(
        keyword: String,
        cursor: String?,
        pageSize: Int,
    ): Result<SearchPage> = videoRepo.searchVideos(keyword, cursor, pageSize)

    override fun getGuessCandidates(
        cursor: String?,
        serveSessionId: String?,
        pageSize: Int,
        refresh: Boolean,
    ): Result<GuessPage> =
        videoRepo.loadSearchGuesses(cursor, serveSessionId, pageSize, refresh)

    override fun reportEvents(
        events: List<RecommendationEvent>,
    ): Result<List<RecommendationEventResult>> = videoRepo.reportEvents(events)

    override fun getHistory(): List<String> = historyManager.getHistory()
    override fun addHistory(keyword: String) = historyManager.addHistory(keyword)
    override fun deleteHistory(keyword: String) = historyManager.deleteHistory(keyword)
    override fun clearHistory() = historyManager.clearAll()
}
