package ru.ainetico.honestprice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.ainetico.honestprice.ocr.RemoteVisionClient

/**
 * App settings stored in SharedPreferences.
 * Sensitive fields (API key, URL, model) use EncryptedSharedPreferences.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        // Migrate: if old default was written to prefs, clear it so we use placeholder instead
        if (prefs.getString("system_prompt", "") == RemoteVisionClient.DEFAULT_SYSTEM_PROMPT) {
            prefs.edit().remove("system_prompt").apply()
        }
        // Migrate sensitive fields from plaintext prefs to encrypted storage
        migrateSensitiveField("api_key")
        migrateSensitiveField("api_url")
        migrateSensitiveField("api_model")
    }

    private fun migrateSensitiveField(key: String) {
        val plainValue = prefs.getString(key, null)
        if (plainValue != null) {
            securePrefs.edit().putString(key, plainValue).apply()
            prefs.edit().remove(key).apply()
        }
    }

    private val _useRemoteServer = MutableStateFlow(prefs.getBoolean("use_remote_server", false))
    val useRemoteServer: StateFlow<Boolean> = _useRemoteServer

    private val _apiUrl = MutableStateFlow(securePrefs.getString("api_url", "") ?: "")
    val apiUrl: StateFlow<String> = _apiUrl

    private val _apiKey = MutableStateFlow(securePrefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey

    private val _apiModel = MutableStateFlow(securePrefs.getString("api_model", "") ?: "")
    val apiModel: StateFlow<String> = _apiModel

    private val _systemPrompt = MutableStateFlow(prefs.getString("system_prompt", "") ?: "")
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _localPrompt = MutableStateFlow(prefs.getString("local_prompt", "") ?: "")
    val localPrompt: StateFlow<String> = _localPrompt

    fun isRemoteModelConfigured(): Boolean {
        return _useRemoteServer.value && _apiUrl.value.isNotBlank() && _apiModel.value.isNotBlank()
    }

    fun setUseRemoteServer(enabled: Boolean) {
        prefs.edit().putBoolean("use_remote_server", enabled).apply()
        _useRemoteServer.value = enabled
    }

    fun setApiUrl(url: String) {
        securePrefs.edit().putString("api_url", url).apply()
        _apiUrl.value = url
    }

    fun setApiKey(key: String) {
        securePrefs.edit().putString("api_key", key).apply()
        _apiKey.value = key
    }

    fun setApiModel(model: String) {
        securePrefs.edit().putString("api_model", model).apply()
        _apiModel.value = model
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString("system_prompt", prompt).apply()
        _systemPrompt.value = prompt
    }

    fun setLocalPrompt(prompt: String) {
        prefs.edit().putString("local_prompt", prompt).apply()
        _localPrompt.value = prompt
    }
}
