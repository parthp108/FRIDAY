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

class SurpriseIntelligenceWorker(
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
        if (currentHour < 10 || currentHour >= 18) {
            return Result.success()
        }

        runCatching {
            val forecast = container.predictiveEngine.forecastEnergy()
            val prompt = "Hey boss, quick AI insight! Optimal focus window today is ${forecast.optimalFocusWindow}. ${forecast.reason}"
            val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)

            ProactiveVoiceActivity.launch(
                context = context,
                message = prompt,
                nudgeType = NUDGE_TYPE,
                nudgeId = nudgeId
            )
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_surprise_intelligence"
        const val NUDGE_TYPE = "surprise_insight"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SurpriseIntelligenceWorker>()
                .addTag("friday_surprise_insight_debug")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SurpriseIntelligenceWorker>(6, TimeUnit.HOURS)
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
