package com.example.flower_show.data.push

import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal interface DeviceRegistrationRemote {
    fun register(installationId: String): DeviceRegistrationResponse
    fun disable(deviceId: String, accessToken: String? = null): DeviceDisableResponse
}

internal class DeviceRegistrationApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val gson: Gson = Gson(),
) : DeviceRegistrationRemote {
    private val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override fun register(installationId: String): DeviceRegistrationResponse {
        val requestBody = DeviceRegistrationRequest(
            token = installationId,
            platform = AndroidPlatform,
            recipientType = FidRecipientType,
        )
        val request = Request.Builder()
            .url(devicesUrl())
            .header("Accept", JsonMediaType.toString())
            .post(gson.toJson(requestBody).toRequestBody(JsonMediaType))
            .build()
        return execute(request, DeviceRegistrationResponse::class.java).also { response ->
            require(response.id.isNotBlank()) { "Device id must not be blank" }
            require(response.platform == AndroidPlatform) { "Unexpected device platform" }
            require(response.recipientType == FidRecipientType) { "Unexpected recipient type" }
            require(response.enabled) { "Registered device must be enabled" }
        }
    }

    override fun disable(deviceId: String, accessToken: String?): DeviceDisableResponse {
        require(deviceId.isNotBlank()) { "Device id must not be blank" }
        val request = Request.Builder()
            .url(devicesUrl().newBuilder().addPathSegment(deviceId).build())
            .header("Accept", JsonMediaType.toString())
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            .delete()
            .build()
        return execute(request, DeviceDisableResponse::class.java)
    }

    private fun devicesUrl(): HttpUrl = baseHttpUrl.newBuilder()
        .addPathSegment("me")
        .addPathSegment("devices")
        .build()

    private fun <T> execute(request: Request, responseType: Class<T>): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DeviceRegistrationApiException(response.code)
            }
            return runCatching { gson.fromJson(body, responseType) }.getOrElse {
                throw DeviceRegistrationApiException(response.code)
            }
        }
    }

    private companion object {
        const val AndroidPlatform = "android"
        const val FidRecipientType = "fid"
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

internal data class DeviceRegistrationRequest(
    val token: String,
    val platform: String,
    val recipientType: String,
) {
    override fun toString(): String =
        "DeviceRegistrationRequest(token=<redacted>, platform=$platform, recipientType=$recipientType)"
}

internal data class DeviceRegistrationResponse(
    val id: String,
    val platform: String,
    val deviceName: String?,
    val recipientType: String,
    val enabled: Boolean,
    val lastSeenAt: String,
)

internal data class DeviceDisableResponse(val changed: Boolean)

internal class DeviceRegistrationApiException(
    val statusCode: Int,
) : Exception("Device registration request failed with HTTP $statusCode")
