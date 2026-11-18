package com.health.friday.settings

import android.content.Context
import com.health.friday.shared.FridayDefaults

class SettingsRepository(
    context: Context
) {

    private val prefs =
        context.applicationContext.getSharedPreferences(
            "friday_settings",
            Context.MODE_PRIVATE
        )

    fun getApiKey(): String {
        return prefs.getString(KEY_API, "")?.trim().orEmpty()
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun getModel(): String {
        val model = prefs.getString(KEY_MODEL, "")?.trim().orEmpty()
        return if (model.isEmpty()) DEFAULT_MODEL else model
    }

    fun saveModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun isLocalLlmEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCAL_LLM, true)
    }

    fun saveLocalLlmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_LLM, enabled).apply()
    }

    companion object {
        const val DEFAULT_MODEL = FridayDefaults.GEMINI_MODEL
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_LOCAL_LLM = "local_llm_enabled"
    }
}
