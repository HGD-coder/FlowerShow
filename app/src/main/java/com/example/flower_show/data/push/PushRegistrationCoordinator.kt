package com.example.flower_show.data.push

import com.example.flower_show.data.auth.AuthSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PushRegistrationCoordinator internal constructor(
    private val sessionManager: AuthSessionManager,
    private val remote: DeviceRegistrationRemote,
    private val store: PushRegistrationStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val operationMutex = Mutex()

    internal suspend fun onRegistered(installationId: String): PushRegistrationSyncResult {
        val normalizedFid = installationId.trim()
        if (normalizedFid.isEmpty()) return PushRegistrationSyncResult.FAILED
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                bestEffortSync {
                    store.saveLatestFid(normalizedFid)
                    syncLocked(forceUpload = true)
                }
            }
        }
    }

    internal suspend fun syncStoredRegistration(): PushRegistrationSyncResult = withContext(ioDispatcher) {
        operationMutex.withLock {
            bestEffortSync { syncLocked(forceUpload = false) }
        }
    }

    internal suspend fun disableCurrentDevice(accessToken: String?): PushDeviceDisableResult =
        withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val deviceId = store.read().deviceId
                    if (deviceId.isNullOrBlank()) {
                        // 没有可注销的设备，顺手清理可能残留的同步记录。
                        store.clearSyncedRegistration()
                        return@withLock PushDeviceDisableResult.NO_DEVICE
                    }
                    if (accessToken.isNullOrBlank()) {
                        return@withLock PushDeviceDisableResult.FAILED
                    }
                    // 先调服务端删除，成功后才清本地记录：若服务端调用失败，
                    // 本地仍保留 deviceId 供下次重试，避免留下无法清理的僵尸设备。
                    remote.disable(deviceId, accessToken)
                    store.clearSyncedRegistration()
                    PushDeviceDisableResult.DISABLED
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    PushDeviceDisableResult.FAILED
                }
            }
        }

    private fun syncLocked(forceUpload: Boolean): PushRegistrationSyncResult {
        val state = store.read()
        val installationId = state.latestFid?.takeIf(String::isNotBlank)
            ?: return PushRegistrationSyncResult.NO_FID
        val userId = sessionManager.currentUserId()?.takeIf(String::isNotBlank)
            ?: return PushRegistrationSyncResult.NO_AUTHENTICATED_USER
        if (
            !forceUpload &&
            state.syncedFid == installationId &&
            state.syncedUserId == userId &&
            !state.deviceId.isNullOrBlank()
        ) {
            return PushRegistrationSyncResult.ALREADY_SYNCED
        }

        val response = remote.register(installationId)
        store.saveSyncedRegistration(
            installationId = installationId,
            userId = userId,
            deviceId = response.id,
        )
        return PushRegistrationSyncResult.SYNCED
    }

    private inline fun bestEffortSync(
        block: () -> PushRegistrationSyncResult,
    ): PushRegistrationSyncResult =
        runCatchingPreservingCancellation(block) ?: PushRegistrationSyncResult.FAILED

    private inline fun <T> runCatchingPreservingCancellation(block: () -> T): T? {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}

internal enum class PushRegistrationSyncResult {
    NO_FID,
    NO_AUTHENTICATED_USER,
    ALREADY_SYNCED,
    SYNCED,
    FAILED,
}

internal enum class PushDeviceDisableResult {
    NO_DEVICE,
    DISABLED,
    FAILED,
}
