package com.example.flower_show.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreRefreshTokenStoreTest {
    private lateinit var context: Context
    private lateinit var store: KeystoreRefreshTokenStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = KeystoreRefreshTokenStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun writeAndRead_roundTripsRefreshToken() {
        val refreshToken = "refresh-${UUID.randomUUID()}"

        store.write(refreshToken)

        assertEquals(refreshToken, store.read())
    }

    @Test
    fun write_persistsOnlyEncryptedValues() {
        val refreshToken = "sensitive-${UUID.randomUUID()}"

        store.write(refreshToken)

        val persistedValues = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .map { value -> value.toString() }
        assertTrue(persistedValues.isNotEmpty())
        assertTrue(persistedValues.none { value -> value.contains(refreshToken) })
    }

    @Test
    fun clear_removesPersistedRefreshToken() {
        store.write("refresh-${UUID.randomUUID()}")

        store.clear()

        assertNull(store.read())
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "auth_encrypted_storage"
    }
}
