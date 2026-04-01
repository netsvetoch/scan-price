package ru.ainetico.honestprice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppSettings(context: Context) {

    private val dataStore = context.dataStore

    private val securePrefs: SharedPreferences = run {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_settings",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // DataStore-backed fields (Flow<T>)
    private object Keys {
        val USE_REMOTE_SERVER = booleanPreferencesKey("use_remote_server")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val LOCAL_PROMPT = stringPreferencesKey("local_prompt")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val useRemoteServer: Flow<Boolean> = dataStore.data.map { it[Keys.USE_REMOTE_SERVER] ?: false }
    val systemPrompt: Flow<String> = dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: "" }
    val localPrompt: Flow<String> = dataStore.data.map { it[Keys.LOCAL_PROMPT] ?: "" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setUseRemoteServer(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_REMOTE_SERVER] = enabled }
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setLocalPrompt(prompt: String) {
        dataStore.edit { it[Keys.LOCAL_PROMPT] = prompt }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    // Encrypted fields (StateFlow<T>, unchanged API)
    private val _apiUrl = MutableStateFlow(securePrefs.getString("api_url", "") ?: "")
    val apiUrl: StateFlow<String> = _apiUrl

    private val _apiKey = MutableStateFlow(securePrefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey

    private val _apiModel = MutableStateFlow(securePrefs.getString("api_model", "") ?: "")
    val apiModel: StateFlow<String> = _apiModel

    fun setApiUrl(url: String) {
        securePrefs.edit().putString("api_url", url).commit()
        _apiUrl.value = url
    }

    fun setApiKey(key: String) {
        securePrefs.edit().putString("api_key", key).commit()
        _apiKey.value = key
    }

    fun setApiModel(model: String) {
        securePrefs.edit().putString("api_model", model).commit()
        _apiModel.value = model
    }

    suspend fun isRemoteModelConfigured(): Boolean {
        return useRemoteServer.first() && _apiUrl.value.isNotBlank() && _apiModel.value.isNotBlank()
    }
}
