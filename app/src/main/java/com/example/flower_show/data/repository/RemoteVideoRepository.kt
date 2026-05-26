package com.example.flower_show.data.repository

import com.example.flower_show.model.CardItem
import com.example.flower_show.model.Result

/**
 * RemoteVideoRepository - Backend API stub
 */
class RemoteVideoRepository : IVideoRepository {
    override fun loadFeed(page: Int, pageSize: Int) =
        Result.error("后端API未配置。设置 RepositoryFactory.USE_REMOTE=false 使用本地数据。")
    override fun search(keyword: String) =
        Result.error("后端搜索API未配置。")
    override fun getRecommendWords(videoId: String) = emptyList<String>()
}
