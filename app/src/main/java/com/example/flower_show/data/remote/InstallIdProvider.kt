package com.example.flower_show.data.remote

import android.content.Context
import java.util.UUID

class InstallIdProvider(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun get(): String = synchronized(Lock) {
        preferences.getString(InstallIdKey, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { generated ->
                check(preferences.edit().putString(InstallIdKey, generated).commit()) {
                    "Unable to persist install id"
                }
            }
    }

    private companion object {
        const val PreferencesName = "recommendation_client"
        const val InstallIdKey = "install_id"
        val Lock = Any()
    }
}
