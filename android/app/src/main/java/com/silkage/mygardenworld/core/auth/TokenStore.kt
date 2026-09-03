package com.silkage.mygardenworld.core.auth

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

/** Persistent auth material. Only the refresh token is sensitive. */
interface TokenStore {
    fun readRefreshToken(): String?
    fun writeRefreshToken(token: String)
    fun clearRefreshToken()
    fun readBaseUrl(): String?
    fun writeBaseUrl(url: String)
    fun readDeviceId(): String?
    fun writeDeviceId(value: String)
}

/** SharedPreferences-backed store; the refresh token is AES-GCM encrypted with an Android Keystore key. */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    override fun readRefreshToken(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)?.let(::decrypt)

    override fun writeRefreshToken(token: String) {
        preferences.edit().putString(KEY_REFRESH_TOKEN, encrypt(token)).apply()
    }

    override fun clearRefreshToken() {
        preferences.edit().remove(KEY_REFRESH_TOKEN).apply()
    }

    override fun readBaseUrl(): String? = preferences.getString(KEY_BASE_URL, null)

    override fun writeBaseUrl(url: String) {
        preferences.edit().putString(KEY_BASE_URL, url).apply()
    }

    override fun readDeviceId(): String? = preferences.getString(KEY_DEVICE_ID, null)

    override fun writeDeviceId(value: String) {
        preferences.edit().putString(KEY_DEVICE_ID, value).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)),
        )
        String(cipher.doFinal(payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mygardenworld.refresh-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_BASE_URL = "base_url"
        const val KEY_DEVICE_ID = "device_id"
    }
}
