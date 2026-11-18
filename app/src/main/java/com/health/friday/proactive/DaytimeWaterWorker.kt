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
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class DaytimeWaterWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (context.applicationContext as FridayApplication).container

        // 1. Daytime restriction: 8 AM to 8 PM (hours 8..19)
        val currentHour = LocalTime.now().hour
        if (currentHour < 8 || currentHour >= 20) {
            return Result.success()
        }

        val todayStr = LocalDate.now().toString()
        val signal = container.signalsDao.getSignalForDate(todayStr)
        val water = signal?.waterMl ?: 0

        // Dynamic Hydration Pacing Calculation (Function of past value, current time & remaining daylight)
        val pacing = DynamicHydrationCalculator.calculatePacing(water)

        // DYNAMIC PACING CHECK: If user is ahead of pace or met goal, SUPPRESS ALL NOTIFICATIONS!
        if (pacing.isAheadOfPace || pacing.promptMessage == null) {
            return Result.success()
        }

        // Adaptive Nudge Policy check
        if (!container.nudgePolicy.canNudgeWater(water, pacing.expectedTargetMl)) {
            return Result.success()
        }

        // Check notifications permission
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.success()
        }

        val speechMessage = pacing.promptMessage
        val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)

        sendWaterNotification(context, water, pacing.expectedTargetMl, nudgeId)

        // Launch Proactive Voice Turn ("Speak-Then-Listen" overlay)
        runCatching {
            ProactiveVoiceActivity.launch(
                context = context,
                message = speechMessage,
                nudgeType = NUDGE_TYPE,
                nudgeId = nudgeId
            )
        }

        return Result.success()
    }

    private fun sendWaterNotification(context: Context, water: Int, expectedTarget: Int, nudgeId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "friday_water_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FRIDAY Daytime Water Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val text = if (water == 0) {
            "You haven't logged any water today yet. Drink a glass of water to stay hydrated!"
        } else {
            "You're at ${water}ml of water today (target: ${expectedTarget}ml). Time for a quick drink!"
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
            .setContentTitle("Hydration Reminder 💧")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .addAction(0, "+250 ml Water", actionIntent(NotificationActionReceiver.ACTION_ADD_WATER_250, 1))
            .addAction(0, "Dismiss", actionIntent(NotificationActionReceiver.ACTION_DISMISS, 2))

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_daytime_water_reminder"
        const val NUDGE_TYPE = NudgePolicy.WATER_REMINDER_TYPE
        private const val NOTIFICATION_ID = 1002

        /** Enqueues an adaptive 5-minute follow-up check when water intake is low (< 700ml). */
        fun scheduleAdaptive5Min(context: Context) {
            val request = OneTimeWorkRequestBuilder<DaytimeWaterWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .addTag("friday_water_adaptive_5m")
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueue(request)
        }

        /** Enqueues a one-time run for immediate testing. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DaytimeWaterWorker>()
                .addTag("friday_water_reminder_debug")
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueue(request)
        }

        /** Schedules recurring 2-hour periodic work for daytime water checks. KEEP preserves existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DaytimeWaterWorker>(2, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }
    }
}
