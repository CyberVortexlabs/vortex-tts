package com.vortex.tts.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences for API Key (AQ.Ab8...).
 * MasterKey AES256_GCM — same store as KeyFragment ("vortex_tts").
 */
class SecurePrefsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "vortex_tts", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "selected_model"
        const val KEY_VOICE = "selected_voice"
        const val KEY_STYLE = "style_prompt"
    }
    fun saveApiKey(key: String) { prefs.edit().putString(KEY_API, key.trim()).apply() }
    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()
    fun isValidFormat(key: String): Boolean {
        val t = key.trim()
        return t.startsWith("AQ.") && t.length >= 20
    }
    fun saveModel(m: String) { prefs.edit().putString(KEY_MODEL, m).apply() }
    fun getModel(): String = prefs.getString(KEY_MODEL, "gemini-2.5-flash-preview-tts") ?: "gemini-2.5-flash-preview-tts"
    fun saveVoice(v: String) { prefs.edit().putString(KEY_VOICE, v).apply() }
    fun getVoice(): String = prefs.getString(KEY_VOICE, "Kore") ?: "Kore"
}
