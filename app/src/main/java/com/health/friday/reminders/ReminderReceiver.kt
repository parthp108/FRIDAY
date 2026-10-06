
package com.health.friday.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.health.friday.MainActivity
import com.health.friday.R
import com.health.friday.data.local.FridayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val pendingResult =
            goAsync()

        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        ).launch {

            try {

                handleReminder(
                    context =
                        context,

                    intent =
                        intent
                )

            } finally {

                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(
        context: Context,
        intent: Intent
    ) {

        val title =
            intent.getStringExtra(
                EXTRA_TITLE
            ) ?: "FRIDAY reminder"

        val message =
            intent.getStringExtra(
                EXTRA_MESSAGE
            ) ?: title

        val repeatDaily =
            intent.getBooleanExtra(
                EXTRA_REPEAT_DAILY,
                false
            )

        val reminderId =
            intent.getLongExtra(
                EXTRA_REMINDER_ID,
                -1L
            )

        val database =
            FridayDatabase.getDatabase(
                context
            )

        val reminder =
            if (reminderId > 0L) {
                database
                    .reminderDao()
                    .getById(
                        reminderId
                    )
            } else {
                null
            }

        /*
         * If the reminder was deleted or disabled before the
         * receiver executed, do nothing.
         */
        if (
            reminderId > 0L &&
            (
                    reminder == null ||
                            !reminder.enabled
                    )
        ) {
            return
        }

        val launchIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        val contentPendingIntent =
            PendingIntent.getActivity(
                context,

                if (reminderId > 0L) {
                    reminderId.toInt()
                } else {
                    System.currentTimeMillis()
                        .toInt()
                },

                launchIntent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,

                "FRIDAY Reminders",

                NotificationManager.IMPORTANCE_HIGH
            )

        notificationManager.createNotificationChannel(
            channel
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
                    title
                )
                .setContentText(
                    message
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    contentPendingIntent
                )
                .build()

        notificationManager.notify(
            if (reminderId > 0L) {
                reminderId.toInt()
            } else {
                System.currentTimeMillis()
                    .toInt()
            },

            notification
        )

        /*
         * One-time reminder:
         *
         * It has fired, so keep it in Room but disable it.
         * This prevents it from being resurrected after reboot.
         */
        if (!repeatDaily) {

            if (reminderId > 0L) {

                ReminderRepository(
                    reminderDao =
                        database.reminderDao(),

                    scheduler =
                        ReminderScheduler(context)
                ).completeOneTimeReminder(
                    reminderId
                )
            }

            return
        }

        /*
         * Daily reminder:
         *
         * Schedule the next occurrence at the same local
         * clock time tomorrow.
         */
        scheduleNextDay(
            context =
                context,

            intent =
                intent,

            reminderId =
                reminderId
        )
    }

    private fun scheduleNextDay(
        context: Context,
        intent: Intent,
        reminderId: Long
    ) {

        val now =
            Calendar.getInstance()

        val nextTrigger =
            Calendar.getInstance().apply {

                set(
                    Calendar.HOUR_OF_DAY,
                    now.get(
                        Calendar.HOUR_OF_DAY
                    )
                )

                set(
                    Calendar.MINUTE,
                    now.get(
                        Calendar.MINUTE
                    )
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )

                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }.timeInMillis

        val title =
            intent.getStringExtra(
                EXTRA_TITLE
            ) ?: "FRIDAY reminder"

        val message =
            intent.getStringExtra(
                EXTRA_MESSAGE
            ) ?: title

        ReminderScheduler(
            context
        ).scheduleReminder(
            reminderId =
                reminderId,

            title =
                title,

            message =
                message,

            triggerAtMillis =
                nextTrigger,

            repeatDaily =
                true
        )
    }

    companion object {

        const val CHANNEL_ID =
            "friday_reminders"

        const val EXTRA_TITLE =
            "extra_title"

        const val EXTRA_MESSAGE =
            "extra_message"

        const val EXTRA_REPEAT_DAILY =
            "extra_repeat_daily"

        const val EXTRA_REMINDER_ID =
            "extra_reminder_id"
    }
}

