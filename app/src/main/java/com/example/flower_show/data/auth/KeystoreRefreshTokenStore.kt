package com.example.flower_show.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreRefreshTokenStore(context: Context) : RefreshTokenStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = synchronized(lock) {
        val encodedCiphertext = preferences.getString(CiphertextKey, null) ?: return null
        val encodedIv = preferences.getString(IvKey, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GcmTagLengthBits, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            String(plaintext, StandardCharsets.UTF_8)
        }.getOrElse {
            clear()
            null
        }
    }

    override fun write(refreshToken: String) = synchronized(lock) {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank" }
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(StandardCharsets.UTF_8))
        val committed = preferences.edit()
            .putString(IvKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CiphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(committed) { "Unable to persist encrypted refresh token" }
    }

    override fun clear() {
        synchronized(lock) {
            val committed = preferences.edit().remove(IvKey).remove(CiphertextKey).commit()
            if (!committed) {
                // 删除整个 prefs 文件是最后手段（该文件仅存这两个 key）。
                // 失败只记日志，不再向上抛：清理路径被 KeyStore/IO 异常打断时，
                // 不应把登录/登出流程整体拖垮。
                runCatching { appContext.deleteSharedPreferences(PreferencesName) }
                    .onFailure { error -> android.util.Log.w(TAG, "Failed to clear encrypted refresh token", error) }
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "KeystoreRefreshTokenStore"
        const val PreferencesName = "auth_encrypted_storage"
        const val CiphertextKey = "refresh_token_ciphertext"
        const val IvKey = "refresh_token_iv"
        const val KeyAlias = "flower_show_refresh_token_key_v1"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128

        val lock = Any()
    }
}
