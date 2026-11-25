package com.health.friday.sync.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FridayApplication ?: return Result.failure()
        val container = app.container

        val security = container.cloudSyncSecurity
        val engine = container.driveSyncEngine

        if (!security.hasLocalMasterKey()) return Result.success()

        return when (val outcome = engine.performAuthorizedSync(applicationContext)) {
            is DriveSyncExecutionResult.Success -> {
                Result.success()
            }
            is DriveSyncExecutionResult.AuthRequired -> {
                Result.failure()
            }
            is DriveSyncExecutionResult.Failed -> {
                val error = outcome.cause
                if (error is CancellationException) {
                    throw error
                }
                Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "friday_drive_sync_periodic"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(
                2, TimeUnit.HOURS
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
