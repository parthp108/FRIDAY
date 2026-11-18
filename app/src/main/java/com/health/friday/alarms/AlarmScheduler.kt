package com.health.friday.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler(
    private val context: Context
) {

    private val alarmManager: AlarmManager? get() = runCatching { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }.getOrNull()

    fun canScheduleExactAlarms(): Boolean {
        val mgr = alarmManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mgr.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore if device doesn't support this intent
            }
        }
    }

    fun scheduleAlarm(
        alarmId: Long,
        title: String,
        triggerAtMillis: Long,
        repeatDaily: Boolean,
        requestCode: Int = alarmId.toInt()
    ): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_REPEAT_DAILY, repeatDaily)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val showPendingIntent = showIntent?.let {
            PendingIntent.getActivity(
                context,
                requestCode,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            triggerAtMillis,
            showPendingIntent
        )

        val mgr = alarmManager ?: return true

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !mgr.canScheduleExactAlarms()) {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                mgr.setAlarmClock(alarmClockInfo, pendingIntent)
            }
            true
        } catch (e: SecurityException) {
            try {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun cancelAlarm(
        alarmId: Long,
        title: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (_: Exception) {}
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_REPEAT_DAILY = "extra_repeat_daily"
        const val SLEEP_GUARD_ALARM_ID = 730730L
    }
}