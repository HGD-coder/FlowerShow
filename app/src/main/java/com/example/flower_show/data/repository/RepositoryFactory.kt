package com.example.flower_show.data.repository

import android.content.Context

/**
 * RepositoryFactory - Manual DI container / 手动依赖注入工厂
 *
 * Change USE_REMOTE=true → entire app switches to backend API.
 */
object RepositoryFactory {
    private const val USE_REMOTE = false

    fun getVideoRepository(context: Context): IVideoRepository =
        if (USE_REMOTE) RemoteVideoRepository()
        else FakeVideoRepository.getInstance(context)

    fun getSearchRepository(context: Context): ISearchRepository =
        if (USE_REMOTE) RemoteSearchRepository()
        else LocalSearchRepository(context)
}
