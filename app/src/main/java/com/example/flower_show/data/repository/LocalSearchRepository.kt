package com.example.flower_show.data.repository

import android.content.Context
import com.example.flower_show.data.local.SearchHistoryManager
import com.example.flower_show.model.CardItem
import com.example.flower_show.model.Result

/**
 * LocalSearchRepository - Local search implementation
 */
class LocalSearchRepository(context: Context) : ISearchRepository {

    private val historyManager = SearchHistoryManager(context)
    private val videoRepo = FakeVideoRepository.getInstance(context)

    override fun search(keyword: String): Result<List<CardItem>> = videoRepo.search(keyword)

    override fun getHistory(): List<String> = historyManager.getHistory()
    override fun addHistory(keyword: String) = historyManager.addHistory(keyword)
    override fun deleteHistory(keyword: String) = historyManager.deleteHistory(keyword)
    override fun clearHistory() = historyManager.clearAll()
}
