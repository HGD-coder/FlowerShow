package com.example.flower_show.data.repository

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.Result

/**
 * ISearchRepository - Search data repository interface (DIP core)
 */
interface ISearchRepository {
    fun search(keyword: String): Result<List<CardItem>>
    fun getHistory(): List<String>
    fun addHistory(keyword: String)
    fun deleteHistory(keyword: String)
    fun clearHistory()
}
