package com.health.friday.alarms

import android.util.Log
import com.health.friday.data.local.DailySignalsDao
import java.time.LocalDate
import java.util.Calendar

class AutonomousWakeEngine(
    private val signalsDao: DailySignalsDao,
    private val alarmRepository: AlarmRepository
) {

    companion object {
        private const val TAG = "AutonomousWakeEngine"
        const val BACKUP_ALARM_TITLE = "FRIDAY Backup Morning Alarm"
    }

    /**
     * Evaluates morning sleep state.
     * - If sleep end is NOT recorded by 6:00–6:50 AM and user went to sleep, pushes 7:00 AM backup alarm.
     * - If sleep end IS recorded or user takes steps before 7:00 AM, automatically cancels the backup alarm.
     */
    suspend fun evaluateMorningWakeState(): String? {
        val nowCal = Calendar.getInstance()
        val hour = nowCal.get(Calendar.HOUR_OF_DAY)
        val minute = nowCal.get(Calendar.MINUTE)

        val todayStr = LocalDate.now().toString()
        val signal = signalsDao.getSignalForDate(todayStr) ?: return null

        val activeAlarms = alarmRepository.getAlarmsNow()
        val existingBackup = activeAlarms.find { it.title == BACKUP_ALARM_TITLE }

        val hasAwoken = signal.sleepEndEpoch != null || (signal.steps ?: 0) >= 50

        // 1. Early Wake Cancellation: If user already woke up or walked 50+ steps, cancel backup alarm
        if (hasAwoken) {
            if (existingBackup != null && existingBackup.enabled) {
                alarmRepository.deleteAlarm(existingBackup)
                Log.i(TAG, "User awoken early (steps: ${signal.steps}, sleepEnd: ${signal.sleepEndEpoch}). Cancelled 7:00 AM backup alarm.")
                return "User awake early — cancelled 7:00 AM backup alarm."
            }
            return null
        }

        // 2. Morning Check Window: 6:00 AM to 6:50 AM
        val isMorningWindow = hour == 6 || (hour == 5 && minute >= 45)

        if (isMorningWindow && existingBackup == null) {
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Ensure target time is in the future
            if (targetCal.timeInMillis > System.currentTimeMillis()) {
                val created = alarmRepository.addAlarm(
                    title = BACKUP_ALARM_TITLE,
                    timeMillis = targetCal.timeInMillis,
                    repeatDaily = false
                )
                if (created != null) {
                    Log.i(TAG, "Sleep end not recorded by ${hour}:${minute}. Pushed 7:00 AM Backup Morning Alarm.")
                    return "Pushed 7:00 AM Backup Morning Alarm."
                }
            }
        }

        return null
    }
}
