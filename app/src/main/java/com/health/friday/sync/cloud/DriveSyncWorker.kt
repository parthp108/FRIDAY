package com.health.friday.sync.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.health.friday.FridayApplication
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FridayApplication ?: return Result.success()
        val container = app.container

        val security = container.cloudSyncSecurity
        val engine = container.driveSyncEngine

        if (!security.hasLocalMasterKey()) return Result.success()

        val accessToken = obtainAccessTokenSilent(applicationContext) ?: return Result.success()

        return try {
            engine.performSync(accessToken)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun obtainAccessTokenSilent(context: Context): String? = suspendCoroutine { continuation ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                continuation.resume(token)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "friday_drive_sync_periodic"
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

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
