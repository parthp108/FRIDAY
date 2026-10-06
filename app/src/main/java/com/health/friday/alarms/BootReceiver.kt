
package com.health.friday.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.health.friday.data.local.FridayDatabase
import com.health.friday.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {

            try {
                restoreAlarms(context)
                restoreReminders(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreAlarms(
        context: Context
    ) {
        val database =
            FridayDatabase.getDatabase(context)

        val alarms =
            database.alarmDao().getEnabled()

        val scheduler =
            AlarmScheduler(context)

        val now =
            System.currentTimeMillis()

        for (alarm in alarms) {

            if (alarm.repeatDaily) {

                val nextTrigger =
                    nextDailyOccurrence(
                        storedTimeMillis = alarm.timeMillis,
                        nowMillis = now
                    )

                scheduler.scheduleAlarm(
                    alarmId = alarm.id,
                    title = alarm.title,
                    triggerAtMillis = nextTrigger,
                    repeatDaily = true
                )

            } else {

                if (alarm.timeMillis > now) {

                    scheduler.scheduleAlarm(
                        alarmId = alarm.id,
                        title = alarm.title,
                        triggerAtMillis = alarm.timeMillis,
                        repeatDaily = false
                    )

                } else {

                    database.alarmDao().disable(
                        alarm.id
                    )
                }
            }
        }
    }

    private suspend fun restoreReminders(
        context: Context
    ) {
        val database =
            FridayDatabase.getDatabase(context)

        val reminders =
            database.reminderDao()
                .getAll()
                .first()
                .filter { it.enabled }

        val scheduler =
            ReminderScheduler(context)

        val now =
            System.currentTimeMillis()

        for (reminder in reminders) {

            if (reminder.repeatDaily) {

                val nextTrigger =
                    nextDailyOccurrence(
                        storedTimeMillis = reminder.timeMillis,
                        nowMillis = now
                    )

                scheduler.scheduleReminder(
                    reminderId = reminder.id,
                    title = reminder.title,
                    message = reminder.title,
                    triggerAtMillis = nextTrigger,
                    repeatDaily = true
                )

            } else {

                if (reminder.timeMillis > now) {

                    scheduler.scheduleReminder(
                        reminderId = reminder.id,
                        title = reminder.title,
                        message = reminder.title,
                        triggerAtMillis = reminder.timeMillis,
                        repeatDaily = false
                    )

                } else {

                    database.reminderDao().disable(
                        reminder.id
                    )
                }
            }
        }
    }

    private fun nextDailyOccurrence(
        storedTimeMillis: Long,
        nowMillis: Long
    ): Long {

        val zone =
            ZoneId.systemDefault()

        val storedTime =
            Instant.ofEpochMilli(
                storedTimeMillis
            ).atZone(zone)

        var next =
            ZonedDateTime.of(
                ZonedDateTime.now(zone).toLocalDate(),
                storedTime.toLocalTime(),
                zone
            )

        if (
            next.toInstant().toEpochMilli() <= nowMillis
        ) {
            next = next.plusDays(1)
        }

        return next.toInstant().toEpochMilli()
    }
}
