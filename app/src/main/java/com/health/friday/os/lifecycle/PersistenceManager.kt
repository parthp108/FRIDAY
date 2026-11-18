package com.health.friday.os.lifecycle

import android.content.Context
import android.provider.Settings

object PersistenceManager {

    /**
     * Uses root shell/Shizuku logic to forcefully enable the Accessibility Service
     * if the OS kills it.
     */
    fun forcefullyEnableAccessibility(context: Context) {
        val componentName = "${context.packageName}/com.health.friday.os.bridge.FridayAccessibilityService"
        
        try {
            // Bypass standard Android permission model via UID 2000 Shell execution (Assumes su or Shizuku)
            val commandEnabled = "settings put secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} $componentName"
            Runtime.getRuntime().exec(arrayOf("su", "-c", commandEnabled))
            
            // Ensure global accessibility master toggle is ON
            val commandToggle = "settings put secure ${Settings.Secure.ACCESSIBILITY_ENABLED} 1"
            Runtime.getRuntime().exec(arrayOf("su", "-c", commandToggle))
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}