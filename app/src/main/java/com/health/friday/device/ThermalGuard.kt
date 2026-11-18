package com.health.friday.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

class ThermalGuard(context: Context) {

    companion object {
        private const val TAG = "FRIDAY_THERMAL"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    @Volatile
    var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
        private set

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            runCatching {
                powerManager?.addThermalStatusListener(context.mainExecutor) { status ->
                    currentThermalStatus = status
                    val statusName = when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> "NONE"
                        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                        else -> "UNKNOWN ($status)"
                    }
                    Log.w(TAG, "Android Thermal Status Changed: $statusName")
                }
            }
        }
    }

    /**
     * Returns true if device thermal status is SEVERE or higher, indicating
     * local LLM inference should be paused/routed to prevent thermal shutdown.
     */
    fun isDeviceOverheating(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        }
        return false
    }
}
