package com.example.flower_show.data.auth

import android.content.Context
import com.example.flower_show.config.NetworkConfig
import com.example.flower_show.data.push.DeviceRegistrationApi
import com.example.flower_show.data.push.PushRegistrationCoordinator
import com.example.flower_show.data.push.SharedPreferencesPushRegistrationStore
import com.example.flower_show.data.remote.SocialApi
import com.example.flower_show.data.remote.chat.ChatRealtimeClient
import com.example.flower_show.data.remote.chat.OkHttpChatRealtimeClient
import com.example.flower_show.data.repository.DefaultSocialRepository
import com.example.flower_show.data.repository.IChatRepository
import com.example.flower_show.data.repository.ISocialRepository
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

object AuthGraph {
    @Volatile private var components: AuthComponents? = null

    internal fun installForTests(testComponents: AuthComponents) {
        synchronized(this) {
            components = testComponents
        }
    }

    internal fun resetForTests() {
        synchronized(this) {
            components = null
        }
    }

    fun get(context: Context): AuthComponents {
        val appContext = context.applicationContext
        return components ?: synchronized(this) {
            components ?: createComponents(appContext).also { components = it }
        }
    }

    private fun createComponents(context: Context): AuthComponents {
        val apiOrigin = NetworkConfig.apiBaseUrl.toHttpUrl()
        val tokenStore = KeystoreRefreshTokenStore(context)
        val sessionManager = AuthSessionManager(tokenStore)
        val publicClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        val authApi = AuthApi(
            client = publicClient,
            authBaseUrl = "${NetworkConfig.apiBaseUrl}/auth",
        )
        val refreshCoordinator = TokenRefreshCoordinator(authApi, sessionManager)
        val authenticatedClient = publicClient.newBuilder()
            .addInterceptor(AuthInterceptor(sessionManager, apiOrigin))
            .authenticator(TokenAuthenticator(refreshCoordinator, apiOrigin))
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        val pushRegistrationCoordinator = PushRegistrationCoordinator(
            sessionManager = sessionManager,
            remote = DeviceRegistrationApi(
                client = authenticatedClient,
                baseUrl = NetworkConfig.apiBaseUrl,
            ),
            store = SharedPreferencesPushRegistrationStore(context),
        )
        return AuthComponents(
            sessionManager = sessionManager,
            repository = DefaultAuthRepository(
                authApi = authApi,
                sessionManager = sessionManager,
                refreshCoordinator = refreshCoordinator,
                pushRegistrationCoordinator = pushRegistrationCoordinator,
            ),
            authenticatedClient = authenticatedClient,
            chatRealtimeClient = OkHttpChatRealtimeClient(
                client = authenticatedClient,
                webSocketUrl = NetworkConfig.chatWebSocketUrl,
            ),
            socialRepository = DefaultSocialRepository(
                SocialApi(
                    client = authenticatedClient,
                    baseUrl = NetworkConfig.apiBaseUrl,
                ),
            ),
            pushRegistrationCoordinator = pushRegistrationCoordinator,
        )
    }
}

data class AuthComponents(
    val sessionManager: AuthSessionManager,
    val repository: AuthRepository,
    val authenticatedClient: OkHttpClient,
    val socialRepository: ISocialRepository? = null,
    val chatRepository: IChatRepository? = null,
    val chatRealtimeClient: ChatRealtimeClient? = null,
    val pushRegistrationCoordinator: PushRegistrationCoordinator? = null,
)
