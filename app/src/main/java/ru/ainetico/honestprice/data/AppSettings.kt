package ru.ainetico.honestprice.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.ainetico.honestprice.ocr.RemoteVisionClient

/**
 * App settings stored in SharedPreferences.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("system_prompt")) {
            prefs.edit().putString("system_prompt", RemoteVisionClient.DEFAULT_SYSTEM_PROMPT).apply()
        }
    }

    private val _apiUrl = MutableStateFlow(prefs.getString("api_url", "") ?: "")
    val apiUrl: StateFlow<String> = _apiUrl

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey

    private val _apiModel = MutableStateFlow(prefs.getString("api_model", "") ?: "")
    val apiModel: StateFlow<String> = _apiModel

    private val _systemPrompt = MutableStateFlow(prefs.getString("system_prompt", "")!!)
    val systemPrompt: StateFlow<String> = _systemPrompt

    fun isRemoteModelConfigured(): Boolean {
        return _apiUrl.value.isNotBlank() && _apiModel.value.isNotBlank()
    }

    fun setApiUrl(url: String) {
        prefs.edit().putString("api_url", url).apply()
        _apiUrl.value = url
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        _apiKey.value = key
    }

    fun setApiModel(model: String) {
        prefs.edit().putString("api_model", model).apply()
        _apiModel.value = model
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString("system_prompt", prompt).apply()
        _systemPrompt.value = prompt
    }
}
