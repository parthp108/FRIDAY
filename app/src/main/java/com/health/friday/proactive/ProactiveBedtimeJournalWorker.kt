package com.health.friday.proactive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ProactiveBedtimeJournalWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? FridayApplication ?: return Result.success()
        val container = app.container

        if (!container.nudgePolicy.canNudge(NUDGE_TYPE)) {
            return Result.success()
        }

        val currentHour = LocalTime.now().hour
        // Target evening wind-down window (21:00 to 23:00 / 9 PM to 11 PM)
        if (currentHour < 21) {
            return Result.success()
        }

        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()

        val entries = container.database.journalDao().getAllNow()
        val hasEntryToday = entries.any { it.createdAt >= startOfDay }

        if (!hasEntryToday) {
            val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)
            ProactiveVoiceActivity.launch(
                context = context,
                message = "Hey boss, it's bedtime and time to wind down for sleep. Should we get ready for sleep?",
                nudgeType = NUDGE_TYPE,
                nudgeId = nudgeId
            )
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_proactive_bedtime_journal"
        const val NUDGE_TYPE = "bedtime_journal"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveBedtimeJournalWorker>()
                .addTag("friday_bedtime_journal_debug")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun scheduleInMinutes(context: Context, minutes: Long) {
            val request = OneTimeWorkRequestBuilder<ProactiveBedtimeJournalWorker>()
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .addTag("friday_bedtime_postponed")
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueue(request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveBedtimeJournalWorker>(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
