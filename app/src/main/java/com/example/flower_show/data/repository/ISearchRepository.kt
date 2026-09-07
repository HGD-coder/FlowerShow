package com.example.flower_show.data.repository

import com.example.flower_show.model.GuessPage
import com.example.flower_show.model.RecommendationEvent
import com.example.flower_show.model.RecommendationEventResult
import com.example.flower_show.model.Result
import com.example.flower_show.model.SearchPage

/**
 * 搜索页专用的数据仓库接口。
 *
 * 它比 IVideoRepository 多了搜索页需要的能力：
 * - 搜索关键词
 * - 读取/保存/删除历史记录
 * - 获取猜你想搜候选词
 *
 * SearchViewModel 依赖这个接口，所以它不需要知道历史记录存在 SharedPreferences，
 * 也不直接依赖网络协议实现。
 */
interface ISearchRepository {
    /**
     * 根据关键词搜索视频/图片/图集内容。
     */
    fun search(
        keyword: String,
        cursor: String? = null,
        pageSize: Int = 20,
    ): Result<SearchPage>

    /**
     * 获取搜索历史。
     */
    fun getHistory(): List<String>

    /**
     * 获取搜索页“猜你想搜”的候选词。
     */
    fun getGuessCandidates(
        cursor: String? = null,
        serveSessionId: String? = null,
        pageSize: Int = 8,
        refresh: Boolean = false,
    ): Result<GuessPage>

    fun reportEvents(events: List<RecommendationEvent>): Result<List<RecommendationEventResult>>

    /**
     * 添加一条搜索历史。
     */
    fun addHistory(keyword: String)

    /**
     * 删除一条搜索历史。
     */
    fun deleteHistory(keyword: String)

    /**
     * 清空全部搜索历史。
     */
    fun clearHistory()
}
