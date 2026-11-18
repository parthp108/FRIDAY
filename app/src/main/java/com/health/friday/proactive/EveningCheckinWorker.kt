package com.health.friday.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import com.health.friday.MainActivity
import com.health.friday.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class EveningCheckinWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (context.applicationContext as FridayApplication).container

        if (!container.nudgePolicy.canNudge(NUDGE_TYPE)) {
            return Result.success()
        }

        // Notifications are disabled / permission denied: don't burn a nudge slot.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.success()
        }

        // Rebuild signals for today
        container.signalsBuilder.buildSignalsForDate(System.currentTimeMillis())

        // ISO date, always ASCII digits (matches SignalsBuilder; Locale-formatted dates can break lookups).
        val todayStr = LocalDate.now().toString()
        val signal = container.signalsDao.getSignalForDate(todayStr)
        val water = signal?.waterMl ?: 0
        val kcal = signal?.kcal ?: 0

        if (kcal == 0 || water < 1000) {
            val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)
            sendEveningNotification(context, water, kcal, nudgeId)
        }

        return Result.success()
    }

    private fun sendEveningNotification(context: Context, water: Int, kcal: Int, nudgeId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "friday_proactive"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FRIDAY Proactive", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val text = if (kcal == 0) {
            "You haven't logged any meals today. Take a second to update your log."
        } else {
            "You're only at ${water}ml of water today. Have a glass before bed?"
        }

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String, requestCode: Int): PendingIntent {
            val i = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                putExtra(NotificationActionReceiver.EXTRA_NUDGE_TYPE, NUDGE_TYPE)
                putExtra(NotificationActionReceiver.EXTRA_NUDGE_ID, nudgeId)
            }
            return PendingIntent.getBroadcast(
                context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Evening Check-in")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .addAction(0, "+250 ml Water", actionIntent(NotificationActionReceiver.ACTION_ADD_WATER_250, 1))
            .addAction(0, "Dismiss", actionIntent(NotificationActionReceiver.ACTION_DISMISS, 2))

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_evening_checkin"
        const val NUDGE_TYPE = "evening_checkin"
        private const val NOTIFICATION_ID = 1001
        private val CHECKIN_TIME: LocalTime = LocalTime.of(20, 0)

        /** Milliseconds from now until the next 8:00 PM local time. */
        fun delayUntilNextCheckinMillis(now: LocalDateTime = LocalDateTime.now()): Long {
            var target = now.toLocalDate().atTime(CHECKIN_TIME)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).toMillis()
        }

        /**
         * Enqueues a one-time run for immediate testing.
         * WorkManager will run EveningCheckinWorker immediately.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<EveningCheckinWorker>()
                .addTag("friday_evening_checkin_debug")
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueue(request)
        }

        /** Daily job whose first run is at the next 8 PM. KEEP preserves an existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EveningCheckinWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNextCheckinMillis(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
