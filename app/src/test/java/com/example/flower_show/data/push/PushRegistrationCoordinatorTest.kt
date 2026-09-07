package com.example.flower_show.data.push

import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthTokenResponse
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.auth.RefreshTokenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushRegistrationCoordinatorTest {
    @Test
    fun onRegisteredWithoutUserPersistsFidWithoutCallingServer() = runTest {
        val fixture = fixture(StandardTestDispatcher(testScheduler))

        val result = fixture.coordinator.onRegistered("fid-without-user")

        assertEquals(PushRegistrationSyncResult.NO_AUTHENTICATED_USER, result)
        assertEquals("fid-without-user", fixture.store.read().latestFid)
        assertEquals(emptyList<String>(), fixture.remote.registeredFids)
        assertNull(fixture.store.read().deviceId)
    }

    @Test
    fun storedSyncDoesNotDuplicateRegistrationForAlreadySyncedFidAndUser() = runTest {
        val fixture = fixture(StandardTestDispatcher(testScheduler), authenticated = true)

        val firstResult = fixture.coordinator.onRegistered("same-fid")
        val repeatedResult = fixture.coordinator.syncStoredRegistration()

        assertEquals(PushRegistrationSyncResult.SYNCED, firstResult)
        assertEquals(PushRegistrationSyncResult.ALREADY_SYNCED, repeatedResult)
        assertEquals(listOf("same-fid"), fixture.remote.registeredFids)
        assertEquals("dev_same", fixture.store.read().deviceId)
    }

    @Test
    fun successfulStoredSyncSavesServerDeviceIdAndSyncedIdentity() = runTest {
        val fixture = fixture(StandardTestDispatcher(testScheduler), authenticated = true)
        fixture.store.saveLatestFid("stored-fid")

        val result = fixture.coordinator.syncStoredRegistration()

        assertEquals(PushRegistrationSyncResult.SYNCED, result)
        assertEquals(listOf("stored-fid"), fixture.remote.registeredFids)
        val state = fixture.store.read()
        assertEquals("stored-fid", state.latestFid)
        assertEquals("stored-fid", state.syncedFid)
        assertEquals(TestUserId, state.syncedUserId)
        assertEquals("dev_same", state.deviceId)
    }

    private fun fixture(
        ioDispatcher: CoroutineDispatcher,
        authenticated: Boolean = false,
    ): CoordinatorFixture {
        val sessionManager = AuthSessionManager(InMemoryRefreshTokenStore())
        if (authenticated) {
            sessionManager.replace(authTokenResponse())
        }
        val store = InMemoryPushRegistrationStore()
        val remote = RecordingDeviceRegistrationRemote()
        return CoordinatorFixture(
            coordinator = PushRegistrationCoordinator(
                sessionManager = sessionManager,
                remote = remote,
                store = store,
                ioDispatcher = ioDispatcher,
            ),
            store = store,
            remote = remote,
        )
    }

    private fun authTokenResponse() = AuthTokenResponse(
        tokenType = "Bearer",
        accessToken = "access-token",
        accessTokenExpiresInSeconds = 900,
        refreshToken = "refresh-token",
        refreshTokenExpiresInSeconds = 2_592_000,
        user = AuthUser(
            userId = TestUserId,
            accountId = "account-id",
            username = "garden.user",
            nickname = "花园用户",
            role = "USER",
        ),
    )

    private companion object {
        const val TestUserId = "server-user-id"
    }
}

private data class CoordinatorFixture(
    val coordinator: PushRegistrationCoordinator,
    val store: InMemoryPushRegistrationStore,
    val remote: RecordingDeviceRegistrationRemote,
)

private class RecordingDeviceRegistrationRemote : DeviceRegistrationRemote {
    val registeredFids = mutableListOf<String>()

    override fun register(installationId: String): DeviceRegistrationResponse {
        registeredFids += installationId
        return DeviceRegistrationResponse(
            id = "dev_same",
            platform = "android",
            deviceName = null,
            recipientType = "fid",
            enabled = true,
            lastSeenAt = "2026-07-24T08:00:00Z",
        )
    }

    override fun disable(deviceId: String, accessToken: String?): DeviceDisableResponse =
        DeviceDisableResponse(changed = true)
}

private class InMemoryPushRegistrationStore : PushRegistrationStore {
    private var state = PushRegistrationState(
        latestFid = null,
        syncedFid = null,
        syncedUserId = null,
        deviceId = null,
    )

    override fun read(): PushRegistrationState = state

    override fun saveLatestFid(installationId: String) {
        state = PushRegistrationState(
            latestFid = installationId,
            syncedFid = state.syncedFid,
            syncedUserId = state.syncedUserId,
            deviceId = state.deviceId,
        )
    }

    override fun saveSyncedRegistration(
        installationId: String,
        userId: String,
        deviceId: String,
    ) {
        state = PushRegistrationState(
            latestFid = installationId,
            syncedFid = installationId,
            syncedUserId = userId,
            deviceId = deviceId,
        )
    }

    override fun clearSyncedRegistration() {
        state = PushRegistrationState(
            latestFid = state.latestFid,
            syncedFid = null,
            syncedUserId = null,
            deviceId = null,
        )
    }
}

private class InMemoryRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}
