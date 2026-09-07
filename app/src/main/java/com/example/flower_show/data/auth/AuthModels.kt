package com.example.flower_show.data.auth

import androidx.compose.runtime.Immutable

data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
) {
    override fun toString(): String =
        "RegisterRequest(username=$username, password=<redacted>, nickname=$nickname, " +
            "avatarUrl=$avatarUrl, bio=$bio)"
}

data class LoginRequest(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequest(username=$username, password=<redacted>)"
}

data class RefreshRequest(val refreshToken: String) {
    override fun toString(): String = "RefreshRequest(refreshToken=<redacted>)"
}

@Immutable
data class AuthUser(
    val userId: String,
    val accountId: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val role: String,
)

data class AuthTokenResponse(
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshTokenExpiresInSeconds: Long,
    val user: AuthUser,
) {
    override fun toString(): String =
        "AuthTokenResponse(tokenType=$tokenType, accessToken=<redacted>, " +
            "accessTokenExpiresInSeconds=$accessTokenExpiresInSeconds, " +
            "refreshToken=<redacted>, refreshTokenExpiresInSeconds=$refreshTokenExpiresInSeconds, " +
            "user=$user)"
}

data class LogoutResponse(val revoked: Boolean)

data class LogoutAllResponse(val revokedCount: Int)

class AuthApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
