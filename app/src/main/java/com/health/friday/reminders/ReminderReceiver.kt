
package com.health.friday.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.health.friday.R
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val title =
            intent.getStringExtra(EXTRA_TITLE)
                ?: "FRIDAY reminder"

        val message =
            intent.getStringExtra(EXTRA_MESSAGE)
                ?: title

        val repeatDaily =
            intent.getBooleanExtra(
                EXTRA_REPEAT_DAILY,
                false
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )

        if (repeatDaily) {
            scheduleNextDay(
                context = context,
                intent = intent
            )
        }
    }

    private fun scheduleNextDay(
        context: Context,
        intent: Intent
    ) {

        val now =
            Calendar.getInstance()

        val nextTrigger =
            Calendar.getInstance().apply {

                set(
                    Calendar.HOUR_OF_DAY,
                    now.get(Calendar.HOUR_OF_DAY)
                )

                set(
                    Calendar.MINUTE,
                    now.get(Calendar.MINUTE)
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

        val reminderId =
            intent.getLongExtra(
                EXTRA_REMINDER_ID,
                System.currentTimeMillis()
            )

        ReminderScheduler(context)
            .scheduleReminder(
                reminderId = reminderId,
                title = title,
                message = message,
                triggerAtMillis = nextTrigger,
                repeatDaily = true
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

