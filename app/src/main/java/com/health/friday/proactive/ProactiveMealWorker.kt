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

class ProactiveMealWorker(
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
        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000)

        val todayMeals = container.database.mealDao().getMealsBetween(startOfDay, endOfDay)

        val mealPrompt = when (currentHour) {
            in 8..11 -> {
                val hasBreakfast = todayMeals.any { it.mealType.equals("BREAKFAST", ignoreCase = true) }
                if (!hasBreakfast) "Hey boss, you haven't logged breakfast today yet. What did you have?" else null
            }
            in 12..15 -> {
                val hasLunch = todayMeals.any { it.mealType.equals("LUNCH", ignoreCase = true) }
                if (!hasLunch) "Hey boss, you haven't logged lunch yet. What did you eat?" else null
            }
            in 18..21 -> {
                val hasDinner = todayMeals.any { it.mealType.equals("DINNER", ignoreCase = true) }
                if (!hasDinner) "Hey boss, you haven't logged dinner yet. What did you have for dinner?" else null
            }
            else -> null
        }

        if (mealPrompt != null) {
            val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)
            ProactiveVoiceActivity.launch(
                context = context,
                message = mealPrompt,
                nudgeType = NUDGE_TYPE,
                nudgeId = nudgeId
            )
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_proactive_meal_check"
        const val NUDGE_TYPE = "meal_reminder"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveMealWorker>()
                .addTag("friday_meal_check_debug")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveMealWorker>(2, TimeUnit.HOURS)
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
