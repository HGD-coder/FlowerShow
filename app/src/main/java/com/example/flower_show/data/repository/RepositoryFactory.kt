package com.example.flower_show.data.repository

import android.content.Context

/**
 * RepositoryFactory - Manual DI container / 手动依赖注入工厂
 */
object RepositoryFactory {
    fun getVideoRepository(context: Context): IVideoRepository =
        FakeVideoRepository.getInstance(context)

    fun getSearchRepository(context: Context): ISearchRepository =
        LocalSearchRepository(context)
}
