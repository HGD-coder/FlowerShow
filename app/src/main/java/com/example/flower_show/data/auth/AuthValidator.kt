package com.example.flower_show.data.auth

import java.nio.charset.StandardCharsets
import java.util.Locale

enum class AuthField {
    Username,
    Password,
    Nickname,
    AvatarUrl,
    Bio,
}

data class AuthValidationResult(
    val fieldErrors: Map<AuthField, String>,
) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

object AuthValidator {
    private val UsernamePattern = Regex("^[A-Za-z0-9._-]+$")

    fun validateRegistration(
        username: String,
        password: String,
        nickname: String,
        bio: String,
    ): AuthValidationResult {
        val errors = buildMap {
            val normalizedUsername = username.trim()
            when {
                normalizedUsername.isEmpty() -> put(AuthField.Username, "请输入用户名")
                normalizedUsername.length !in 3..32 ->
                    put(AuthField.Username, "用户名需要 3-32 个字符")
                !UsernamePattern.matches(normalizedUsername) ->
                    put(AuthField.Username, "仅支持英文字母、数字、点、下划线和短横线")
            }

            val passwordCharacters = password.codePointLength()
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8).size
            when {
                password.isEmpty() -> put(AuthField.Password, "请输入密码")
                passwordCharacters < 8 -> put(AuthField.Password, "密码至少需要 8 个字符")
                passwordCharacters > 72 -> put(AuthField.Password, "密码最多 72 个字符")
                passwordBytes > 72 -> put(AuthField.Password, "密码不能超过 72 个 UTF-8 字节")
            }

            val trimmedNickname = nickname.trim()
            when {
                trimmedNickname.isEmpty() -> put(AuthField.Nickname, "请输入昵称")
                trimmedNickname.codePointLength() > 80 ->
                    put(AuthField.Nickname, "昵称最多 80 个字符")
            }

            if (bio.codePointLength() > 255) {
                put(AuthField.Bio, "简介最多 255 个字符")
            }
        }
        return AuthValidationResult(errors)
    }

    fun validateLogin(username: String, password: String): AuthValidationResult {
        val errors = buildMap {
            if (username.trim().isEmpty()) put(AuthField.Username, "请输入用户名")
            if (password.isEmpty()) put(AuthField.Password, "请输入密码")
        }
        return AuthValidationResult(errors)
    }

    fun normalizeUsername(username: String): String = username.trim().lowercase(Locale.ROOT)

    fun optionalText(value: String): String? = value.trim().takeIf(String::isNotEmpty)

    private fun String.codePointLength(): Int = codePointCount(0, length)
}
