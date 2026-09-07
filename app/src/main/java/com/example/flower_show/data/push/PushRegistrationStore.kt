package com.example.flower_show.data.push

import android.content.Context

internal interface PushRegistrationStore {
    fun read(): PushRegistrationState
    fun saveLatestFid(installationId: String)
    fun saveSyncedRegistration(installationId: String, userId: String, deviceId: String)
    fun clearSyncedRegistration()
}

internal class SharedPreferencesPushRegistrationStore(context: Context) : PushRegistrationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    override fun read(): PushRegistrationState = synchronized(lock) {
        PushRegistrationState(
            latestFid = preferences.getString(LatestFidKey, null),
            syncedFid = preferences.getString(SyncedFidKey, null),
            syncedUserId = preferences.getString(SyncedUserIdKey, null),
            deviceId = preferences.getString(DeviceIdKey, null),
        )
    }

    override fun saveLatestFid(installationId: String) {
        require(installationId.isNotBlank()) { "Installation id must not be blank" }
        synchronized(lock) {
            check(preferences.edit().putString(LatestFidKey, installationId).commit()) {
                "Unable to persist Firebase installation id"
            }
        }
    }

    override fun saveSyncedRegistration(
        installationId: String,
        userId: String,
        deviceId: String,
    ) {
        require(installationId.isNotBlank()) { "Installation id must not be blank" }
        require(userId.isNotBlank()) { "User id must not be blank" }
        require(deviceId.isNotBlank()) { "Device id must not be blank" }
        synchronized(lock) {
            check(
                preferences.edit()
                    .putString(LatestFidKey, installationId)
                    .putString(SyncedFidKey, installationId)
                    .putString(SyncedUserIdKey, userId)
                    .putString(DeviceIdKey, deviceId)
                    .commit(),
            ) {
                "Unable to persist device registration"
            }
        }
    }

    override fun clearSyncedRegistration() {
        synchronized(lock) {
            check(
                preferences.edit()
                    .remove(SyncedFidKey)
                    .remove(SyncedUserIdKey)
                    .remove(DeviceIdKey)
                    .commit(),
            ) {
                "Unable to clear device registration"
            }
        }
    }

    private companion object {
        const val PreferencesName = "push_registration"
        const val LatestFidKey = "latest_fid"
        const val SyncedFidKey = "synced_fid"
        const val SyncedUserIdKey = "synced_user_id"
        const val DeviceIdKey = "server_device_id"
    }
}

internal class PushRegistrationState(
    val latestFid: String?,
    val syncedFid: String?,
    val syncedUserId: String?,
    val deviceId: String?,
) {
    override fun toString(): String =
        "PushRegistrationState(latestFid=<redacted>, syncedFid=<redacted>, " +
            "syncedUserId=$syncedUserId, deviceId=$deviceId)"
}
