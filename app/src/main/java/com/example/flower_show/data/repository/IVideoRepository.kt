package com.example.flower_show.data.repository

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.Result

/**
 * IVideoRepository - Video data repository interface (DIP core)
 * ViewModel depends on this interface, never on concrete implementation.
 */
interface IVideoRepository {
    fun loadFeed(page: Int, pageSize: Int): Result<List<CardItem>>
    fun search(keyword: String): Result<List<CardItem>>
    fun getRecommendWords(videoId: String): List<String>
}
