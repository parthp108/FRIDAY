
package com.health.friday.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class AlarmScheduler(
    private val context: Context
) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent =
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            context.startActivity(intent)
        }
    }

    fun scheduleAlarm(
        alarmId: Long,
        title: String,
        triggerAtMillis: Long,
        repeatDaily: Boolean,
        requestCode: Int = alarmId.toInt()
    ): Boolean {

        /*
         * FRIDAY alarms are normal alarms.
         *
         * Do not silently downgrade them to an inexact alarm.
         * If exact-alarm permission is unavailable, report failure
         * so the caller knows the requested alarm time cannot be
         * guaranteed.
         */
        if (!canScheduleExactAlarms()) {
            return false
        }

        val intent =
            Intent(
                context,
                AlarmReceiver::class.java
            ).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REPEAT_DAILY, repeatDaily)
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return try {

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )

            true

        } catch (_: SecurityException) {

            false

        } catch (_: Exception) {

            false
        }
    }

    fun cancelAlarm(
        alarmId: Long,
        requestCode: Int = alarmId.toInt()
    ) {

        val intent =
            Intent(
                context,
                AlarmReceiver::class.java
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_REPEAT_DAILY = "extra_repeat_daily"
    }
}

