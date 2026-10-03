package com.health.friday.settings

import android.content.Context

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

        val model =
            prefs.getString(KEY_MODEL, "")?.trim().orEmpty()

        return if (model.isEmpty()) DEFAULT_MODEL else model
    }

    fun saveModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    companion object {

        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

        private const val KEY_API = "gemini_api_key"

        private const val KEY_MODEL = "gemini_model"
    }
}