package com.example.flower_show.data.repository

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.Result

/**
 * RemoteSearchRepository - Backend search API stub
 */
class RemoteSearchRepository : ISearchRepository {
    override fun search(keyword: String) = Result.error("后端搜索API未配置。")
    override fun getHistory() = emptyList<String>()
    override fun addHistory(keyword: String) = Unit
    override fun deleteHistory(keyword: String) = Unit
    override fun clearHistory() = Unit
}
