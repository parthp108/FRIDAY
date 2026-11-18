package com.health.friday.agentic

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class BluetoothControlTool(
    private val context: Context
) : AiTool {

    override val name = "control_bluetooth"

    override val description =
        "Toggles Bluetooth on or off, or opens Bluetooth settings screen."

    override val parameters = listOf(
        AiToolParameter("state", "'on', 'off', or 'toggle'.", required = true)
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val state = arguments["state"]?.trim()?.lowercase().orEmpty()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            return "Bluetooth is not supported on this device."
        }

        return when (state) {
            "on", "enable" -> {
                if (adapter.isEnabled) {
                    "Bluetooth is already ON."
                } else {
                    tryToggleBluetooth(adapter, true)
                }
            }
            "off", "disable" -> {
                if (!adapter.isEnabled) {
                    "Bluetooth is already OFF."
                } else {
                    tryToggleBluetooth(adapter, false)
                }
            }
            "toggle" -> {
                val newState = !adapter.isEnabled
                tryToggleBluetooth(adapter, newState)
            }
            else -> "Invalid state '$state'. Use 'on' or 'off'."
        }
    }

    private fun tryToggleBluetooth(adapter: BluetoothAdapter, enable: Boolean): String {
        return try {
            val success = try {
                if (enable) {
                    @Suppress("DEPRECATION")
                    adapter.enable()
                } else {
                    @Suppress("DEPRECATION")
                    adapter.disable()
                }
            } catch (e: SecurityException) {
                false
            }

            if (success) {
                "Bluetooth ${if (enable) "ENABLED" else "DISABLED"}."
            } else {
                openBluetoothSettings()
                "Opened Bluetooth Settings to ${if (enable) "enable" else "disable"} Bluetooth."
            }
        } catch (e: Exception) {
            Log.w("FRIDAY_BLUETOOTH", "Direct Bluetooth toggle failed, opening settings", e)
            openBluetoothSettings()
            "Opened Bluetooth Settings to adjust Bluetooth state."
        }
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("FRIDAY_BLUETOOTH", "Could not open Bluetooth settings", e)
        }
    }
}
