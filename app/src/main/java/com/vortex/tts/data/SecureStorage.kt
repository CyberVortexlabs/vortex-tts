package com.vortex.tts.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    @Suppress("DEPRECATION")
    private val preferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "vortex_tts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String? = preferences.getString(API_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveApiKey(value: String) {
        preferences.edit { putString(API_KEY, value.trim()) }
    }

    fun clearApiKey() {
        preferences.edit { remove(API_KEY) }
    }

    companion object {
        private const val API_KEY = "gemini_api_key"
    }
}
