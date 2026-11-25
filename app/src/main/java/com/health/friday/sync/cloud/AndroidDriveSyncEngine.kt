package com.health.friday.sync.cloud

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import com.health.friday.shared.sync.cloud.CloudSyncStore
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume

data class DriveSyncResult(
    val backfilledRecords: Int,
    val uploadedBatches: Int,
    val uploadedOperations: Int,
    val downloadedBatches: Int,
    val appliedOperations: Int
)

sealed interface DriveAuthorizationResult {
    data class Authorized(val accessToken: String) : DriveAuthorizationResult

    data class UserActionRequired(
        val pendingIntent: PendingIntent
    ) : DriveAuthorizationResult

    data class Unavailable(
        val cause: Throwable
    ) : DriveAuthorizationResult
}

sealed interface DriveSyncExecutionResult {
    data class Success(
        val result: DriveSyncResult
    ) : DriveSyncExecutionResult

    data class AuthRequired(
        val pendingIntent: PendingIntent? = null
    ) : DriveSyncExecutionResult

    data class TransientFailure(
        val cause: Throwable
    ) : DriveSyncExecutionResult

    data class PermanentFailure(
        val cause: Throwable
    ) : DriveSyncExecutionResult
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) {
                continuation.resume(value)
            }
        }

        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(error))
            }
        }

        addOnCanceledListener {
            continuation.cancel()
        }
    }

suspend fun obtainDriveAuthorization(
    context: Context
): DriveAuthorizationResult {
    val request = AuthorizationRequest.builder()
        .setRequestedScopes(
            listOf(
                Scope("https://www.googleapis.com/auth/drive.file")
            )
        )
        .build()

    return try {
        val result: AuthorizationResult =
            Identity.getAuthorizationClient(context.applicationContext)
                .authorize(request)
                .awaitTask()

        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: return DriveAuthorizationResult.Unavailable(
                    IllegalStateException(
                        "Authorization requires user interaction but no PendingIntent was returned."
                    )
                )

            DriveAuthorizationResult.UserActionRequired(pendingIntent)
        } else {
            val token = result.accessToken
                ?.takeIf { it.isNotBlank() }

            if (token != null) {
                DriveAuthorizationResult.Authorized(token)
            } else {
                DriveAuthorizationResult.Unavailable(
                    IllegalStateException(
                        "Google authorization succeeded without returning an access token."
                    )
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DriveAuthorizationResult.Unavailable(e)
    }
}

class AndroidDriveSyncEngine(
    private val cloudSyncStore: CloudSyncStore,
    private val security: AndroidCloudSyncSecurity,
    private val deviceId: String,
    private val backfill: CloudSyncBackfill,
    private val drive: DriveVaultClient = DriveVaultClient(),
    val statusRepository: DriveSyncStatusRepository? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var isSyncing = false

    fun classifyFailure(cause: Throwable): DriveSyncExecutionResult {
        if (cause is DriveAuthException) {
            return DriveSyncExecutionResult.AuthRequired()
        }

        if (cause is DriveAuthorizationRequiredException) {
            return DriveSyncExecutionResult.AuthRequired(cause.pendingIntent)
        }

        if (cause is CancellationException) {
            throw cause
        }

        if (cause is DriveHttpException) {
            return if (cause.isTransient) {
                DriveSyncExecutionResult.TransientFailure(cause)
            } else {
                DriveSyncExecutionResult.PermanentFailure(cause)
            }
        }

        if (cause is SocketTimeoutException ||
            cause is UnknownHostException ||
            cause is IOException ||
            (cause is ApiException && (
                cause.statusCode == CommonStatusCodes.NETWORK_ERROR ||
                cause.statusCode == CommonStatusCodes.TIMEOUT ||
                cause.statusCode == CommonStatusCodes.INTERNAL_ERROR
            ))
        ) {
            return DriveSyncExecutionResult.TransientFailure(cause)
        }

        return DriveSyncExecutionResult.PermanentFailure(cause)
    }

    fun triggerInstantSync(context: Context) {
        if (!security.hasLocalMasterKey()) return

        val appContext = context.applicationContext
        syncScope.launch {
            if (isSyncing) return@launch

            isSyncing = true
            try {
                when (val outcome = performAuthorizedSync(appContext)) {
                    is DriveSyncExecutionResult.Success,
                    is DriveSyncExecutionResult.AuthRequired,
                    is DriveSyncExecutionResult.TransientFailure,
                    is DriveSyncExecutionResult.PermanentFailure -> {
                        // performAuthorizedSync() owns status transitions.
                        // Background retry scheduling is handled separately.
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusRepository?.updateStatus(
                    DriveSyncState.FAILED,
                    e.message ?: e.javaClass.simpleName
                )
            } finally {
                isSyncing = false
            }
        }
    }

    suspend fun performAuthorizedSync(
        context: Context
    ): DriveSyncExecutionResult {
        if (!security.hasLocalMasterKey()) {
            val cause = IllegalStateException("FRIDAY cloud key is not initialized.")
            statusRepository?.updateStatus(DriveSyncState.FAILED, cause.message)
            return DriveSyncExecutionResult.PermanentFailure(cause)
        }

        statusRepository?.updateStatus(DriveSyncState.SYNCING)

        return when (val authorization = obtainDriveAuthorization(context.applicationContext)) {
            is DriveAuthorizationResult.Authorized ->
                executeSyncWithToken(
                    context = context.applicationContext,
                    accessToken = authorization.accessToken,
                    retryAfter401 = true
                )

            is DriveAuthorizationResult.UserActionRequired -> {
                statusRepository?.updateStatus(
                    DriveSyncState.AUTH_REQUIRED,
                    "Google Drive authorization required."
                )
                DriveSyncExecutionResult.AuthRequired(authorization.pendingIntent)
            }

            is DriveAuthorizationResult.Unavailable -> {
                val failure = classifyFailure(authorization.cause)
                statusRepository?.updateStatus(
                    if (failure is DriveSyncExecutionResult.AuthRequired)
                        DriveSyncState.AUTH_REQUIRED else DriveSyncState.FAILED,
                    authorization.cause.message
                )
                failure
            }
        }
    }

    /**
     * Foreground sync entry point for DriveConnectionActivity.
     * The Activity obtains the token through the foreground consent flow,
     * then calls this method. A 401 triggers one further authorization attempt.
     */
    suspend fun performSyncWithAccessToken(
        context: Context,
        accessToken: String
    ): DriveSyncExecutionResult {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }

        if (!security.hasLocalMasterKey()) {
            val cause = IllegalStateException("FRIDAY cloud key is not initialized.")
            statusRepository?.updateStatus(DriveSyncState.FAILED, cause.message)
            return DriveSyncExecutionResult.PermanentFailure(cause)
        }

        statusRepository?.updateStatus(DriveSyncState.SYNCING)

        return executeSyncWithToken(
            context = context.applicationContext,
            accessToken = accessToken,
            retryAfter401 = true
        )
    }

    private suspend fun executeSyncWithToken(
        context: Context,
        accessToken: String,
        retryAfter401: Boolean
    ): DriveSyncExecutionResult {
        try {
            val result = performSync(accessToken)
            statusRepository?.updateStatus(DriveSyncState.SUCCESS)
            return DriveSyncExecutionResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (firstError: DriveAuthException) {
            if (!retryAfter401) {
                statusRepository?.updateStatus(
                    DriveSyncState.AUTH_REQUIRED,
                    firstError.message
                )
                return DriveSyncExecutionResult.AuthRequired()
            }

            // A 401 can occur after a long-running sync. Ask Google Identity
            // for a fresh token and retry the complete sync at most once.
            return when (val authorization = obtainDriveAuthorization(context)) {
                is DriveAuthorizationResult.Authorized -> {
                    try {
                        val result = performSync(authorization.accessToken)
                        statusRepository?.updateStatus(DriveSyncState.SUCCESS)
                        DriveSyncExecutionResult.Success(result)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (secondError: DriveAuthException) {
                        statusRepository?.updateStatus(
                            DriveSyncState.AUTH_REQUIRED,
                            "Google Drive rejected the renewed authorization."
                        )
                        DriveSyncExecutionResult.AuthRequired()
                    } catch (e: Exception) {
                        val failure = classifyFailure(e)
                        statusRepository?.updateStatus(
                            if (failure is DriveSyncExecutionResult.AuthRequired)
                                DriveSyncState.AUTH_REQUIRED else DriveSyncState.FAILED,
                            e.message ?: e.javaClass.simpleName
                        )
                        failure
                    }
                }

                is DriveAuthorizationResult.UserActionRequired -> {
                    statusRepository?.updateStatus(
                        DriveSyncState.AUTH_REQUIRED,
                        "Google Drive authorization required."
                    )
                    DriveSyncExecutionResult.AuthRequired(authorization.pendingIntent)
                }

                is DriveAuthorizationResult.Unavailable -> {
                    val failure = classifyFailure(authorization.cause)
                    statusRepository?.updateStatus(
                        if (failure is DriveSyncExecutionResult.AuthRequired)
                            DriveSyncState.AUTH_REQUIRED else DriveSyncState.FAILED,
                        authorization.cause.message
                    )
                    failure
                }
            }
        } catch (e: Exception) {
            val failure = classifyFailure(e)
            statusRepository?.updateStatus(
                if (failure is DriveSyncExecutionResult.AuthRequired)
                    DriveSyncState.AUTH_REQUIRED else DriveSyncState.FAILED,
                e.message ?: e.javaClass.simpleName
            )
            return failure
        }
    }

    suspend fun backUpRecoveryKey(
        accessToken: String,
        passphrase: CharArray
    ): CloudSyncKeyEnvelope = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        val envelope = security.createRecoveryEnvelope(passphrase)
        val folderId = drive.ensureVaultFolder(accessToken)
        val fileName = "${DriveVaultClient.RECOVERY_FILE_PREFIX}${envelope.createdAtMs}${DriveVaultClient.RECOVERY_FILE_SUFFIX}"
        val content = json.encodeToString(CloudSyncKeyEnvelope.serializer(), envelope)
        drive.uploadTextFile(
            accessToken = accessToken,
            folderId = folderId,
            fileName = fileName,
            content = content,
            appProperties = mapOf("fridayKind" to "key-envelope", "keyId" to envelope.keyId)
        )
        envelope
    }

    suspend fun restoreRecoveryKey(
        accessToken: String,
        passphrase: CharArray
    ): CloudSyncKeyEnvelope = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        val folderId = drive.ensureVaultFolder(accessToken)
        val file = drive.findLatestRecoveryEnvelope(accessToken, folderId)
            ?: error("No FRIDAY recovery-key envelope was found in this Google Drive vault")
        val content = drive.downloadTextFile(accessToken, file.id)
        val envelope = json.decodeFromString(CloudSyncKeyEnvelope.serializer(), content)
        envelope.validate()
        security.restoreFromEnvelope(envelope, passphrase)
        envelope
    }

    suspend fun performSync(accessToken: String): DriveSyncResult = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        check(security.hasLocalMasterKey()) {
            "FRIDAY cloud key is not initialized. Back up or restore the recovery key first."
        }
        val folderId = drive.ensureVaultFolder(accessToken)
        val backfillReport = backfill.run()
        val uploadResult = uploadPendingOutbox(accessToken, folderId)
        val downloadResult = downloadRemoteBatches(accessToken, folderId)
        DriveSyncResult(
            backfilledRecords = backfillReport.totalRecords,
            uploadedBatches = uploadResult.first,
            uploadedOperations = uploadResult.second,
            downloadedBatches = downloadResult.first,
            appliedOperations = downloadResult.second
        )
    }

    private suspend fun uploadPendingOutbox(
        accessToken: String,
        folderId: String
    ): Pair<Int, Int> {
        var batchCount = 0
        var operationCount = 0

        repeat(MAX_BATCHES_PER_SYNC) {
            val pending = cloudSyncStore.pendingOutbox(limit = MAX_OPERATIONS_PER_BATCH)
            if (pending.isEmpty()) return batchCount to operationCount

            val nowMs = System.currentTimeMillis()
            val batchId = StableCloudBatchId.from(deviceId, pending)
            val batch = SyncBatch(
                batchId = batchId,
                originDeviceId = deviceId,
                createdAtMs = nowMs,
                operations = pending
            ).also { it.validate() }

            val encrypted = security.encryptBatch(batch)
            val payload = json.encodeToString(EncryptedSyncPayload.serializer(), encrypted)
            val fileName = "${DriveVaultClient.BATCH_FILE_PREFIX}${batch.batchId}${DriveVaultClient.BATCH_FILE_SUFFIX}"

            // If the process died after a successful upload but before markUploaded,
            // this finds the deterministic name and avoids creating a second copy.
            val existingFile = drive.findTextFile(accessToken, folderId, fileName)
            if (existingFile == null) {
                drive.uploadTextFile(
                    accessToken = accessToken,
                    folderId = folderId,
                    fileName = fileName,
                    content = payload,
                    appProperties = mapOf("fridayKind" to "sync-batch", "batchId" to batch.batchId)
                )
            } else {
                // Verify the previous upload before marking this outbox set complete.
                val previousJson = drive.downloadTextFile(accessToken, existingFile.id)
                val previousEncrypted = json.decodeFromString(
                    EncryptedSyncPayload.serializer(),
                    previousJson
                )
                val previousBatch = security.decryptBatch(previousEncrypted)
                check(previousBatch.batchId == batchId) {
                    "Existing Drive batch filename contains a different batch ID"
                }
                check(previousBatch.operations.map { it.operationId }.toSet() ==
                    batch.operations.map { it.operationId }.toSet()) {
                    "Existing Drive batch does not contain the expected outbox operations"
                }
            }

            cloudSyncStore.markUploaded(
                operationIds = pending.map { it.operationId },
                batchId = batch.batchId,
                uploadedAtMs = nowMs
            )
            batchCount++
            operationCount += pending.size
        }

        return batchCount to operationCount
    }

    private suspend fun downloadRemoteBatches(
        accessToken: String,
        folderId: String
    ): Pair<Int, Int> {
        val currentCursor = cloudSyncStore.readCursor()
        val files = drive.listBatchFiles(accessToken, folderId)
        var downloaded = 0
        var applied = 0

        for (file in files) {
            // Do not silently skip corrupt or undecryptable FRIDAY batch files.
            // A failed batch is surfaced to the user and does not advance the
            // Room inbox/cursor transaction for that batch.
            val content = drive.downloadTextFile(accessToken, file.id)
            val encrypted = json.decodeFromString(EncryptedSyncPayload.serializer(), content)
            val batch = security.decryptBatch(encrypted)
            val nextCursor = SyncCursor(
                drivePageToken = currentCursor.drivePageToken,
                lastSuccessfulSyncAtMs = System.currentTimeMillis()
            )
            val result = cloudSyncStore.ingestRemoteBatch(batch, nextCursor)
            downloaded++
            applied += result.appliedOperations
        }
        return downloaded to applied
    }

    companion object {
        private const val MAX_OPERATIONS_PER_BATCH = 200
        private const val MAX_BATCHES_PER_SYNC = 50
    }
}
