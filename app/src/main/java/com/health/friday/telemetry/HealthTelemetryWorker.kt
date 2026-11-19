package com.health.friday.telemetry

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import com.health.friday.proactive.DaytimeWaterWorker
import com.health.friday.proactive.EveningCheckinWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class HealthTelemetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val appContext = context.applicationContext

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Health telemetry worker started")

            val application = appContext as FridayApplication
            val container = application.container

            // 1. Refresh the last 3 days of signals. Meals, water and tasks do
            //    not need Health Connect, so this always runs.
            container.signalsBuilder.buildRecentDays(3)

            // 2. Protocols run on fresh signals and do NOT need Health Connect
            //    (e.g. the screen-time rule), so they run before the access check.
            try {
                container.protocolEngine.seedDefaultsIfEmpty()
                container.protocolEngine.evaluateAndTrigger()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Protocol engine failed", e)
            }

            // 3. Anomaly detection needs Health Connect data.
            if (!container.healthConnectRepository.hasAccess()) {
                Log.w(TAG, "Health Connect access unavailable")
                return@withContext Result.success()
            }

            container.anomalyDetector.detectAndNotify()

            Log.d(TAG, "Health telemetry worker completed")
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Health telemetry worker failed", exception)
            // Periodic work: retry later instead of marking this run a permanent failure.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HealthTelemetryWorker"
        const val UNIQUE_PERIODIC_WORK_NAME = "friday_telemetry_sync"

        /**
         * Enqueues a one-time run for immediate testing.
         * WorkManager may delay execution briefly.
         */
        fun runNow(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<HealthTelemetryWorker>()
                    .addTag("friday_telemetry_debug")
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueue(request)

            Log.d(TAG, "One-time telemetry run enqueued")
        }

        /**
         * Schedules the recurring four-hour telemetry check (battery-not-low) and the
         * daily 8 PM evening check-in. KEEP preserves existing schedules.
         *
         * Deliberately NOT RequiresDeviceIdle: an idle device means the screen is off,
         * so rules about live phone use (e.g. screen time at 10:30 PM) would never fire.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request =
                PeriodicWorkRequestBuilder<HealthTelemetryWorker>(
                    4, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .addTag(UNIQUE_PERIODIC_WORK_NAME)
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            EveningCheckinWorker.schedule(context)
            DaytimeWaterWorker.schedule(context)
            com.health.friday.device.HealthConnectArchiveWorker.schedulePeriodic(context)
        }
    }
}
