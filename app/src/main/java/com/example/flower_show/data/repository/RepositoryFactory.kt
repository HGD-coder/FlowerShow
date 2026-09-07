package com.example.flower_show.data.repository

import android.content.Context
import com.example.flower_show.config.NetworkConfig
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.data.remote.InstallIdProvider
import com.example.flower_show.data.remote.SocialApi
import com.example.flower_show.data.remote.chat.ChatApi

/**
 * Repository 工厂，手写版依赖注入容器。
 *
 * 你可以把它理解成：
 * “项目现在要用哪个 Repository 实现，都在这里决定。”
 *
 * 正常大项目可能会用 Hilt/Koin 这类依赖注入框架；
 * 当前项目规模不大，所以先用 object 工厂集中创建 Repository。
 */
object RepositoryFactory {
    @Volatile private var videoRepository: IVideoRepository? = null
    @Volatile private var socialRepository: ISocialRepository? = null
    @Volatile private var chatRepository: IChatRepository? = null

    private fun getOrCreateVideoRepository(context: Context): IVideoRepository {
        val appContext = context.applicationContext
        return videoRepository ?: synchronized(this) {
            videoRepository ?: ApiVideoRepository(
                client = AuthGraph.get(appContext).authenticatedClient,
                installId = InstallIdProvider(appContext).get(),
            ).also { videoRepository = it }
        }
    }

    /**
     * 首页视频流使用的仓库。
     *
     * 生产环境固定返回认证 OkHttpClient 驱动的 ApiVideoRepository。
     */
    fun getVideoRepository(context: Context): IVideoRepository =
        getOrCreateVideoRepository(context)

    fun getSocialRepository(context: Context): ISocialRepository {
        val appContext = context.applicationContext
        return socialRepository ?: synchronized(this) {
            socialRepository ?: DefaultSocialRepository(
                SocialApi(
                    client = AuthGraph.get(appContext).authenticatedClient,
                    baseUrl = NetworkConfig.apiBaseUrl,
                ),
            ).also { socialRepository = it }
        }
    }

    fun getChatRepository(context: Context): IChatRepository {
        val appContext = context.applicationContext
        return chatRepository ?: synchronized(this) {
            chatRepository ?: DefaultChatRepository(
                ChatApi(
                    client = AuthGraph.get(appContext).authenticatedClient,
                    baseUrl = NetworkConfig.apiBaseUrl,
                ),
            ).also { chatRepository = it }
        }
    }

    /**
     * 搜索页使用的仓库。
     *
     * LocalSearchRepository 只管理本地历史；搜索与猜词委托网络仓库。
     */
    fun getSearchRepository(context: Context): ISearchRepository =
        LocalSearchRepository(context, getOrCreateVideoRepository(context))
}
