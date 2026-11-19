package com.health.friday.device

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class HealthConnectArchiveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FridayApplication ?: return Result.success()
        val container = app.container

        val repo = container.healthConnectRepository
        val syncer = container.healthConnectArchiveSyncer

        if (!repo.isAvailable()) return Result.success()
        if (!repo.hasAccess()) return Result.success()
        if (!repo.hasBackgroundReadAccess()) return Result.success()

        return try {
            val report = syncer.syncAllRecordTypes()

            if (report.failuresByRecordType.isNotEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "health_connect_archive_periodic"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthConnectArchiveWorker>(
                6, TimeUnit.HOURS
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
        }
    }
}
