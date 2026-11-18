package com.health.friday.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.health.friday.data.local.FridayDatabase
import com.health.friday.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime


class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action

        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                restoreAlarms(appContext)
                restoreReminders(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Restore after $action failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreAlarms(context: Context) {
        val alarmDao = FridayDatabase.getDatabase(context).alarmDao()
        val scheduler = AlarmScheduler(context)
        val now = System.currentTimeMillis()

        for (alarm in alarmDao.getEnabled()) {
            val trigger = nextTrigger(
                storedMillis = alarm.timeMillis,
                repeatDaily = alarm.repeatDaily,
                nowMillis = now
            )

            if (trigger == null) {
                alarmDao.disable(alarm.id)
                continue
            }

            scheduler.scheduleAlarm(
                alarmId = alarm.id,
                title = alarm.title,
                triggerAtMillis = trigger,
                repeatDaily = alarm.repeatDaily
            )
        }
    }

    private suspend fun restoreReminders(context: Context) {
        val reminderDao = FridayDatabase.getDatabase(context).reminderDao()
        val scheduler = ReminderScheduler(context)
        val now = System.currentTimeMillis()

        val reminders = reminderDao
            .getAll()
            .first()
            .filter { it.enabled }

        for (reminder in reminders) {
            val trigger = nextTrigger(
                storedMillis = reminder.timeMillis,
                repeatDaily = reminder.repeatDaily,
                nowMillis = now
            )

            if (trigger == null) {
                reminderDao.disable(reminder.id)
                continue
            }

            scheduler.scheduleReminder(
                reminderId = reminder.id,
                title = reminder.title,
                message = reminder.title,
                triggerAtMillis = trigger,
                repeatDaily = reminder.repeatDaily
            )
        }
    }

    /**
     * When should this reminder fire next? Null means: drop it.
     *
     * - Still in the future: keep the stored instant (this also covers
     *   overnight one-time reminders, which the old code disabled by mistake).
     * - One-time, missed while the phone was off (within 12 h): fire shortly.
     * - One-time, missed long ago: drop it.
     * - Daily: next occurrence of the stored time of day.
     */
    private fun nextTrigger(
        storedMillis: Long,
        repeatDaily: Boolean,
        nowMillis: Long
    ): Long? {

        if (storedMillis > nowMillis) {
            return storedMillis
        }

        if (!repeatDaily) {
            return if (nowMillis - storedMillis <= MISSED_GRACE_MS) {
                nowMillis + MISSED_FIRE_DELAY_MS
            } else {
                null
            }
        }

        val zone = ZoneId.systemDefault()
        val timeOfDay = Instant.ofEpochMilli(storedMillis).atZone(zone).toLocalTime()

        var next = ZonedDateTime.of(LocalDate.now(zone), timeOfDay, zone)

        if (next.toInstant().toEpochMilli() <= nowMillis) {
            next = next.plusDays(1)
        }

        return next.toInstant().toEpochMilli()
    }

    private companion object {
        const val TAG = "FRIDAY.Boot"
        const val MISSED_GRACE_MS = 12L * 60 * 60 * 1000
        const val MISSED_FIRE_DELAY_MS = 5_000L
    }
}
