
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
import androidx.core.app.NotificationCompat
import com.health.friday.R
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val alarmId =
            intent.getLongExtra(
                AlarmScheduler.EXTRA_ALARM_ID,
                -1L
            )

        val title =
            intent.getStringExtra(
                AlarmScheduler.EXTRA_TITLE
            ) ?: "FRIDAY alarm"

        val repeatDaily =
            intent.getBooleanExtra(
                AlarmScheduler.EXTRA_REPEAT_DAILY,
                false
            )

        if (alarmId == -1L) {
            return
        }

        /*
         * A daily alarm is rescheduled by the receiver itself.
         *
         * This is important because the receiver is the component
         * Android wakes independently of the FRIDAY UI.
         */
        if (repeatDaily) {

            val scheduler =
                AlarmScheduler(context)

            val nextTrigger =
                Calendar.getInstance().apply {

                    add(
                        Calendar.DAY_OF_YEAR,
                        1
                    )

                    set(
                        Calendar.SECOND,
                        0
                    )

                    set(
                        Calendar.MILLISECOND,
                        0
                    )
                }.timeInMillis

            scheduler.scheduleAlarm(
                alarmId = alarmId,
                title = title,
                triggerAtMillis = nextTrigger,
                repeatDaily = true
            )
        }

        createAlarmChannel(context)

        val alarmActivityIntent =
            Intent(
                context,
                AlarmActivity::class.java
            ).apply {

                putExtra(
                    AlarmScheduler.EXTRA_ALARM_ID,
                    alarmId
                )

                putExtra(
                    AlarmScheduler.EXTRA_TITLE,
                    title
                )

                putExtra(
                    AlarmScheduler.EXTRA_REPEAT_DAILY,
                    repeatDaily
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                context,
                alarmId.toInt(),
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    "ALARM"
                )
                .setContentText(
                    title
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )
                .setPriority(
                    NotificationCompat.PRIORITY_MAX
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(
                    fullScreenPendingIntent,
                    true
                )
                .build()

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        notificationManager.notify(
            notificationId(alarmId),
            notification
        )
    }

    companion object {

        const val CHANNEL_ID =
            "friday_alarms_v2"

        fun notificationId(
            alarmId: Long
        ): Int {
            return (
                    alarmId xor
                            (alarmId ushr 32)
                    ).toInt()
        }

        private fun createAlarmChannel(
            context: Context
        ) {

            if (
                Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O
            ) {
                return
            }

            val notificationManager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            val alarmSound =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                )

            val audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_ALARM
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "FRIDAY Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "FRIDAY alarm clock"

                    setSound(
                        alarmSound,
                        audioAttributes
                    )

                    enableVibration(true)

                    lockscreenVisibility =
                        NotificationCompat.VISIBILITY_PUBLIC
                }

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }
}

