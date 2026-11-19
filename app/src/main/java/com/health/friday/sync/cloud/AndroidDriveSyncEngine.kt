package com.health.friday.sync.cloud

import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import com.health.friday.shared.sync.cloud.CloudSyncStore
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DriveSyncResult(
    val backfilledRecords: Int,
    val uploadedBatches: Int,
    val uploadedOperations: Int,
    val downloadedBatches: Int,
    val appliedOperations: Int
)

/**
 * Manual, foreground Drive sync. Background scheduling is intentionally not
 * enabled until the recovery and two-device round-trip have been verified.
 */
class AndroidDriveSyncEngine(
    private val cloudSyncStore: CloudSyncStore,
    private val security: AndroidCloudSyncSecurity,
    private val deviceId: String,
    private val backfill: CloudSyncBackfill,
    private val drive: DriveVaultClient = DriveVaultClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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
                check(previousBatch.batchId == batch.batchId) {
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
