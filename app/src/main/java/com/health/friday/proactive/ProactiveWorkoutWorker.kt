package com.health.friday.proactive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ProactiveWorkoutWorker(
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
        // Target evening activity window (17:00 to 19:00 / 5 PM to 7 PM)
        if (currentHour < 17 || currentHour >= 20) {
            return Result.success()
        }

        runCatching {
            val healthSummary = container.healthConnectRepository.getTodayHealth()
            if (healthSummary.steps < 4000) {
                val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)
                ProactiveVoiceActivity.launch(
                    context = context,
                    message = "Hey boss, time for your evening walk or run! Ready to move?",
                    nudgeType = NUDGE_TYPE,
                    nudgeId = nudgeId
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_proactive_workout_check"
        const val NUDGE_TYPE = "workout_reminder"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveWorkoutWorker>()
                .addTag("friday_workout_check_debug")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveWorkoutWorker>(2, TimeUnit.HOURS)
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
