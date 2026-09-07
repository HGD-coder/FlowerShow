package com.example.flower_show.viewmodel

sealed interface AuthIntent {
    data object ShowLogin : AuthIntent
    data object ShowRegister : AuthIntent
    data class UsernameChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data class NicknameChanged(val value: String) : AuthIntent
    data class AvatarUrlChanged(val value: String) : AuthIntent
    data class BioChanged(val value: String) : AuthIntent
    data object TogglePasswordVisibility : AuthIntent
    data object Submit : AuthIntent
    data object DismissMessage : AuthIntent
    data object Logout : AuthIntent
    data object LogoutAll : AuthIntent
}

