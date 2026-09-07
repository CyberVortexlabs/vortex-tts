package com.vortex.tts.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class ApiKeyEntry(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: Long
)

class SecureStorage(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

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

    private fun migrateIfNeeded() {
        val json = preferences.getString(KEYS_JSON, null)
        if (!json.isNullOrBlank()) return
        val legacy = preferences.getString(API_KEY, null)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val entry = ApiKeyEntry(
            id = UUID.randomUUID().toString(),
            name = "Default",
            key = legacy,
            createdAt = System.currentTimeMillis()
        )
        saveKeysInternal(listOf(entry))
        preferences.edit { putString(ACTIVE_KEY_ID, entry.id) }
        preferences.edit { remove(API_KEY) }
    }

    fun getApiKeys(): List<ApiKeyEntry> {
        migrateIfNeeded()
        val json = preferences.getString(KEYS_JSON, null) ?: return emptyList()
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ApiKeyEntry>>() {}.type
            gson.fromJson<List<ApiKeyEntry>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getActiveKeyId(): String? {
        migrateIfNeeded()
        val id = preferences.getString(ACTIVE_KEY_ID, null)?.takeIf { it.isNotBlank() }
        if (id != null && getApiKeys().any { it.id == id }) return id
        val first = getApiKeys().firstOrNull()?.id
        if (first != null) preferences.edit { putString(ACTIVE_KEY_ID, first) }
        return first
    }

    fun setActiveKeyId(id: String) {
        if (getApiKeys().any { it.id == id }) {
            preferences.edit { putString(ACTIVE_KEY_ID, id) }
        }
    }

    fun getActiveApiKey(): String? {
        val id = getActiveKeyId() ?: return null
        return getApiKeys().firstOrNull { it.id == id }?.key
    }

    // Legacy single-key API for backward compatibility
    fun getApiKey(): String? = getActiveApiKey()

    fun saveApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        migrateIfNeeded()
        val keys = getApiKeys().toMutableList()
        val activeId = getActiveKeyId()
        if (keys.isEmpty()) {
            val entry = ApiKeyEntry(UUID.randomUUID().toString(), "Default", trimmed, System.currentTimeMillis())
            keys.add(entry)
            saveKeysInternal(keys)
            preferences.edit { putString(ACTIVE_KEY_ID, entry.id) }
        } else if (activeId != null) {
            val idx = keys.indexOfFirst { it.id == activeId }
            if (idx >= 0) {
                keys[idx] = keys[idx].copy(key = trimmed)
                saveKeysInternal(keys)
            } else {
                val entry = ApiKeyEntry(UUID.randomUUID().toString(), "Default", trimmed, System.currentTimeMillis())
                keys.add(entry)
                saveKeysInternal(keys)
            }
        } else {
            val entry = ApiKeyEntry(UUID.randomUUID().toString(), "Default", trimmed, System.currentTimeMillis())
            keys.add(entry)
            saveKeysInternal(keys)
            preferences.edit { putString(ACTIVE_KEY_ID, entry.id) }
        }
    }

    fun addApiKey(name: String, key: String): ApiKeyEntry {
        migrateIfNeeded()
        val entry = ApiKeyEntry(UUID.randomUUID().toString(), name.trim().ifEmpty { "Key" }, key.trim(), System.currentTimeMillis())
        val keys = getApiKeys().toMutableList()
        keys.add(entry)
        saveKeysInternal(keys)
        if (keys.size == 1) preferences.edit { putString(ACTIVE_KEY_ID, entry.id) }
        return entry
    }

    fun updateApiKey(id: String, name: String, key: String): Boolean {
        val keys = getApiKeys().toMutableList()
        val idx = keys.indexOfFirst { it.id == id }
        if (idx < 0) return false
        keys[idx] = keys[idx].copy(name = name.trim().ifEmpty { keys[idx].name }, key = key.trim())
        saveKeysInternal(keys)
        return true
    }

    fun deleteApiKey(id: String): Boolean {
        val keys = getApiKeys().toMutableList()
        val removed = keys.removeIf { it.id == id }
        if (!removed) return false
        saveKeysInternal(keys)
        val active = preferences.getString(ACTIVE_KEY_ID, null)
        if (active == id) {
            val next = keys.firstOrNull()?.id
            if (next != null) preferences.edit { putString(ACTIVE_KEY_ID, next) } else preferences.edit { remove(ACTIVE_KEY_ID) }
        }
        return true
    }

    fun clearApiKey() {
        preferences.edit { remove(API_KEY); remove(KEYS_JSON); remove(ACTIVE_KEY_ID) }
    }

    private fun saveKeysInternal(keys: List<ApiKeyEntry>) {
        val json = gson.toJson(keys)
        preferences.edit { putString(KEYS_JSON, json) }
    }

    companion object {
        private const val API_KEY = "gemini_api_key"
        private const val KEYS_JSON = "gemini_api_keys_json"
        private const val ACTIVE_KEY_ID = "gemini_active_key_id"
    }
}
