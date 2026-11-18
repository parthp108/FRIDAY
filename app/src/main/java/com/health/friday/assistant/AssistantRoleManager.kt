package com.health.friday.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import rikka.shizuku.Shizuku

object AssistantRoleManager {

    fun isDefaultAssistant(context: Context): Boolean {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
        } else null
        val holdsRole = roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

        val activeService = Settings.Secure.getString(
            context.contentResolver,
            "voice_interaction_service"
        )
        val expectedComponent = ComponentName(
            context,
            FridayVoiceInteractionService::class.java
        ).flattenToString()

        return holdsRole || activeService == expectedComponent
    }

    fun requestAssistantRole(
        activity: Activity,
        roleLauncher: ActivityResultLauncher<Intent>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                runCatching { roleLauncher.launch(intent) }.onFailure {
                    launchVoiceSettings(activity)
                }
            } else {
                launchVoiceSettings(activity)
            }
        } else {
            launchVoiceSettings(activity)
        }
    }

    private fun launchVoiceSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun enforceAssistantBindingViaShizuku(context: Context) {
        if (!Shizuku.pingBinder()) return

        val voiceServiceComponent = ComponentName(
            context,
            FridayVoiceInteractionService::class.java
        ).flattenToString()

        val recognitionComponent = ComponentName(
            context,
            FridayRecognitionService::class.java
        ).flattenToString()

        val commands = listOf(
            "settings put secure assistant $voiceServiceComponent",
            "settings put secure voice_interaction_service $voiceServiceComponent",
            "settings put secure assist_structure_enabled 1",
            "settings put secure assist_screenshot_enabled 1",
            "settings put secure assist_disclosure_enabled 0"
        )

        try {
            for (cmd in commands) {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
