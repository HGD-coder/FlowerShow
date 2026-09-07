package com.example.flower_show.data.auth

interface RefreshTokenStore {
    fun read(): String?
    fun write(refreshToken: String)
    fun clear()
}

