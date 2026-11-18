
package com.health.friday.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.health.friday.FridayApplication
import com.health.friday.R
import com.health.friday.data.local.FridayDatabase
import com.health.friday.proactive.ProactiveSleepGuardWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "FRIDAY alarm"
        val repeatDaily = intent.getBooleanExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, false)

        if (alarmId == -1L) {
            return
        }

        if (alarmId == AlarmScheduler.SLEEP_GUARD_ALARM_ID && action == null) {
            handleSleepGuardAlarm(context)
            return
        }

        when (action) {
            ACTION_DISMISS -> {
                handleDismiss(context, alarmId, repeatDaily)
            }
            ACTION_SNOOZE -> {
                handleSnooze(context, alarmId, title, repeatDaily)
            }
            else -> {
                handleAlarmTrigger(context, alarmId, title, repeatDaily)
            }
        }
    }

    private fun handleSleepGuardAlarm(context: Context) {
        val app = context.applicationContext as? FridayApplication
        val container = app?.container

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var isAlreadyAwake = false
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val targetWakeTime = LocalTime.of(7, 30)
                val targetWakeEpoch = today.atTime(targetWakeTime).atZone(zoneId).toInstant().toEpochMilli()

                if (container != null) {
                    runCatching {
                        val dayHealth = container.healthConnectRepository.getDayHealth(startOfDay)
                        val sleepEnd = dayHealth?.sleepEndMillis
                        if (sleepEnd != null && sleepEnd <= targetWakeEpoch) {
                            isAlreadyAwake = true
                        }
                    }
                }

                if (isAlreadyAwake) {
                    Log.i("AlarmReceiver", "User is already awake at 7:30 AM. Skipping wake alarm.")
                } else {
                    Log.i("AlarmReceiver", "User is NOT awake at 7:30 AM. Ringing wake alarm!")
                    val title = "7:30 AM Wake Up Call - Time to get out of bed!"
                    AlarmRingingService.startService(context, AlarmScheduler.SLEEP_GUARD_ALARM_ID, title, true)

                    val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmScheduler.SLEEP_GUARD_ALARM_ID)
                        putExtra(AlarmScheduler.EXTRA_TITLE, title)
                        putExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, true)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }

                    try {
                        context.startActivity(alarmActivityIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Reschedule for next day 7:30 AM
                ProactiveSleepGuardWorker.scheduleExactAlarm(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAlarmTrigger(
        context: Context,
        alarmId: Long,
        title: String,
        repeatDaily: Boolean
    ) {
        // Reschedule daily alarm
        if (repeatDaily) {
            val scheduler = AlarmScheduler(context)
            val nextTrigger = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            scheduler.scheduleAlarm(
                alarmId = alarmId,
                title = title,
                triggerAtMillis = nextTrigger,
                repeatDaily = true
            )
        }

        // 1. Start ongoing Foreground Service (plays looping sound & holds ongoing notification)
        AlarmRingingService.startService(context, alarmId, title, repeatDaily)

        // 2. Launch full-screen AlarmActivity directly
        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, repeatDaily)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        try {
            context.startActivity(alarmActivityIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleDismiss(context: Context, alarmId: Long, repeatDaily: Boolean) {
        AlarmRingingService.stopService(context)

        // Broadcast to finish AlarmActivity if active
        context.sendBroadcast(Intent(AlarmActivity.ACTION_FINISH_ALARM))

        if (!repeatDaily) {
            val database = FridayDatabase.getDatabase(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                database.alarmDao().disable(alarmId)
            }
        }
    }

    private fun handleSnooze(context: Context, alarmId: Long, title: String, repeatDaily: Boolean) {
        AlarmRingingService.stopService(context)

        // Broadcast to finish AlarmActivity if active
        context.sendBroadcast(Intent(AlarmActivity.ACTION_FINISH_ALARM))

        val scheduler = AlarmScheduler(context.applicationContext)
        scheduler.scheduleAlarm(
            alarmId = alarmId,
            title = title,
            triggerAtMillis = System.currentTimeMillis() + (10 * 60 * 1000L), // 10 minutes
            repeatDaily = false,
            requestCode = alarmId.toInt() + 1_000_000
        )

        if (!repeatDaily) {
            val database = FridayDatabase.getDatabase(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                database.alarmDao().disable(alarmId)
            }
        }
    }

    companion object {

        const val CHANNEL_ID = "friday_alarms_v2"
        const val ACTION_DISMISS = "com.health.friday.alarms.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.health.friday.alarms.ACTION_SNOOZE"

        fun notificationId(alarmId: Long): Int {
            return (alarmId xor (alarmId ushr 32)).toInt()
        }

        fun showAlarmNotification(
            context: Context,
            alarmId: Long,
            title: String,
            repeatDaily: Boolean
        ) {
            createAlarmChannel(context)

            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, repeatDaily)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                alarmId.toInt(),
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
                setAction(ACTION_DISMISS)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, repeatDaily)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                (alarmId + 100000).toInt(),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                setAction(ACTION_SNOOZE)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, repeatDaily)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                (alarmId + 200000).toInt(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("ALARM")
                .setContentText(title)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(0, "Dismiss", dismissPendingIntent)
                .addAction(0, "Snooze", snoozePendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId(alarmId), notification)
        }

        fun cancelAlarmNotification(context: Context, alarmId: Long) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId(alarmId))
        }

        private fun createAlarmChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "FRIDAY Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "FRIDAY alarm clock"
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }
}

