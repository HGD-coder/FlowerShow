package com.example.flower_show.viewmodel

import androidx.compose.runtime.Immutable
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.data.auth.AuthUser

enum class AuthMode {
    Login,
    Register,
}

@Immutable
data class AuthState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val passwordVisible: Boolean = false,
    val isInitializing: Boolean = true,
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<AuthField, String> = emptyMap(),
    val message: String? = null,
    val currentUser: AuthUser? = null,
) {
    val isAuthenticated: Boolean get() = currentUser != null
}

