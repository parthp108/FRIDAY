package com.health.friday.telemetry.ble

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.health.friday.data.local.ActionLogDao
import com.health.friday.data.local.ActionLogEntity
import com.health.friday.proactive.NudgePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class AutonomicReflexController(
    private val context: Context,
    private val bleClient: WearableBleGattClient,
    private val actionLogDao: ActionLogDao,
    private val nudgePolicy: NudgePolicy
) {
    companion object {
        private const val TAG = "FRIDAY_AUTONOMIC_REFLEX"
        private const val COOLDOWN_MS = 45 * 60_000L // 45-minute minimum gap between reflex triggers
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastTriggerEpochMs = 0L

    fun startReflexLoop() {
        scope.launch {
            bleClient.stressEvents.collect { snapshot ->
                if (snapshot.isAcuteStressState) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerEpochMs >= COOLDOWN_MS) {
                        lastTriggerEpochMs = now
                        engageStressShield(snapshot)
                    }
                }
            }
        }
    }

    private suspend fun engageStressShield(snapshot: BiometricStressSnapshot) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val prevFilter = nm.currentInterruptionFilter
        val prevNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val maxNotifVol = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }

        val targetQuietVol = (maxNotifVol * 0.15f).toInt().coerceAtLeast(0)
        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetQuietVol, 0)

        val inversePayload = JSONObject().apply {
            put("previousInterruptionFilter", prevFilter)
            put("previousNotificationVolume", prevNotifVol)
        }.toString()

        actionLogDao.insertAction(
            ActionLogEntity(
                timestamp = snapshot.timestampMs,
                toolName = "autonomic_stress_shield",
                description = "Auto-engaged DND & lowered notification volume (HR=${snapshot.currentHrBpm} BPM, RMSSD=${"%.1f".format(snapshot.rmssdMs)} ms)",
                inverseTool = "restore_audio_interruption_state",
                inversePayloadJson = inversePayload
            )
        )

        Log.w(
            TAG,
            "ACUTE STRESS DETECTED: HR=${snapshot.currentHrBpm} BPM (Z=${"%.2f".format(snapshot.hrZScore)}), " +
                    "RMSSD=${"%.1f".format(snapshot.rmssdMs)} ms (Z=${"%.2f".format(snapshot.hrvZScore)}). " +
                    "Engaged DND & attenuated volume."
        )
    }
}

class BleSimulationReceiver(
    private val bleClient: WearableBleGattClient
) : BroadcastReceiver() {
    companion object {
        const val ACTION_SIMULATE_BLE_STRESS = "com.health.friday.ACTION_SIMULATE_BLE_STRESS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_SIMULATE_BLE_STRESS) {
            val simulatedStressPacket = byteArrayOf(
                0x10, 0x68,
                0x4E, 0x02,
                0x50, 0x02,
                0x4D, 0x02,
                0x4F, 0x02,
                0x4E, 0x02
            )
            repeat(4) {
                bleClient.processIncomingGattBytes(simulatedStressPacket)
            }
        }
    }
}
