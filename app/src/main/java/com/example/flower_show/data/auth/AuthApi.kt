package com.example.flower_show.data.auth

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthApi(
    private val client: OkHttpClient,
    private val authBaseUrl: String,
    private val gson: Gson = Gson(),
) {
    fun register(request: RegisterRequest): AuthTokenResponse = executeTokenRequest(
        path = "/register",
        jsonBody = gson.toJson(request),
        expectedStatus = 201,
    )

    fun login(request: LoginRequest): AuthTokenResponse = executeTokenRequest(
        path = "/login",
        jsonBody = gson.toJson(request),
        expectedStatus = 200,
    )

    fun refresh(refreshToken: String): AuthTokenResponse = executeTokenRequest(
        path = "/refresh",
        jsonBody = gson.toJson(RefreshRequest(refreshToken)),
        expectedStatus = 200,
    )

    fun currentAccount(accessToken: String): AuthUser {
        val request = Request.Builder()
            .url(url("/me"))
            .header("Accept", JsonMediaType.toString())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return execute(request, 200) { body -> gson.fromJson(body, AuthUser::class.java) }
    }

    fun logout(accessToken: String, refreshToken: String): LogoutResponse {
        val request = authenticatedPost(
            path = "/logout",
            accessToken = accessToken,
            jsonBody = gson.toJson(RefreshRequest(refreshToken)),
        )
        return execute(request, 200) { body -> gson.fromJson(body, LogoutResponse::class.java) }
    }

    fun logoutAll(accessToken: String): LogoutAllResponse {
        val request = authenticatedPost(
            path = "/logout-all",
            accessToken = accessToken,
            jsonBody = "{}",
        )
        return execute(request, 200) { body -> gson.fromJson(body, LogoutAllResponse::class.java) }
    }

    private fun executeTokenRequest(
        path: String,
        jsonBody: String,
        expectedStatus: Int,
    ): AuthTokenResponse {
        val request = Request.Builder()
            .url(url(path))
            .header("Accept", JsonMediaType.toString())
            .post(jsonBody.toRequestBody(JsonMediaType))
            .build()
        return execute(request, expectedStatus) { body ->
            gson.fromJson(body, AuthTokenResponse::class.java).also(::validateTokenResponse)
        }
    }

    private fun authenticatedPost(
        path: String,
        accessToken: String,
        jsonBody: String,
    ): Request = Request.Builder()
        .url(url(path))
        .header("Accept", JsonMediaType.toString())
        .header("Authorization", "Bearer $accessToken")
        .post(jsonBody.toRequestBody(JsonMediaType))
        .build()

    private fun <T> execute(request: Request, expectedStatus: Int, parse: (String) -> T): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code != expectedStatus) {
                throw AuthApiException(response.code, parseErrorMessage(response.code, body))
            }
            return runCatching { parse(body) }.getOrElse {
                throw AuthApiException(response.code, "Invalid authentication response")
            }
        }
    }

    private fun validateTokenResponse(response: AuthTokenResponse) {
        require(response.tokenType.equals("Bearer", ignoreCase = true))
        require(response.accessToken.isNotBlank())
        require(response.refreshToken.isNotBlank())
        require(response.user.userId.isNotBlank())
    }

    private fun parseErrorMessage(statusCode: Int, body: String): String {
        val parsed = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return parsed?.get("message")?.takeUnless { it.isJsonNull }?.asString
            ?: parsed?.get("error")?.takeUnless { it.isJsonNull }?.asString
            ?: "Authentication request failed with HTTP $statusCode"
    }

    private fun url(path: String): String = authBaseUrl.trimEnd('/') + path

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

