package com.example.flower_show.data.auth

interface AuthRepository {
    suspend fun register(request: RegisterRequest): Result<AuthUser>
    suspend fun login(request: LoginRequest): Result<AuthUser>
    suspend fun restoreSession(): Result<AuthUser>
    suspend fun logout(): Result<Unit>
    suspend fun logoutAll(): Result<Unit>
}

