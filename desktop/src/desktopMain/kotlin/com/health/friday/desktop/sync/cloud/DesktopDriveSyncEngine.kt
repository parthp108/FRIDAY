package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.sync.drive.DesktopDriveVaultClient
import com.health.friday.desktop.sync.drive.DesktopGoogleDriveOAuth
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

data class DesktopDriveSyncResult(
    val uploadedBatches: Int,
    val uploadedOperations: Int,
    val downloadedBatches: Int,
    val appliedOperations: Int,
    val duplicateOrStaleOperations: Int
)

/** Foreground Windows Drive transport for the shared FRIDAY encrypted batch protocol. */
class DesktopDriveSyncEngine internal constructor(
    private val deviceId: String,
    private val oauth: DesktopGoogleDriveOAuth,
    private val keyVault: DesktopCloudSyncKeyVault,
    private val security: DesktopCloudSyncSecurity,
    private val drive: DesktopDriveVaultClient,
    private val store: DesktopCloudSyncStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val syncMutex = Mutex()

    suspend fun performSync(): DesktopDriveSyncResult =
        syncMutex.withLock {
            performSyncLocked()
        }

    private suspend fun performSyncLocked(): DesktopDriveSyncResult {
        require(deviceId.isNotBlank())
        val masterKey = keyVault.load()
            ?: error("FRIDAY desktop cloud key is not initialized. Restore the recovery key first.")
        try {
            val accessToken = oauth.accessToken()
            val folderId = drive.ensureVaultFolder(accessToken)
            val upload = uploadPending(accessToken, folderId, masterKey)
            val download = downloadBatches(accessToken, folderId, masterKey)
            return DesktopDriveSyncResult(
                uploadedBatches = upload.first,
                uploadedOperations = upload.second,
                downloadedBatches = download.first,
                appliedOperations = download.second.first,
                duplicateOrStaleOperations = download.second.second
            )
        } finally {
            masterKey.destroy()
        }
    }

    private suspend fun uploadPending(
        accessToken: String,
        folderId: String,
        masterKey: PortableCloudMasterKey
    ): Pair<Int, Int> {
        var uploadedBatches = 0
        var uploadedOperations = 0

        repeat(MAX_BATCHES_PER_SYNC) {
            val pending = store.pendingOutbox(MAX_OPERATIONS_PER_BATCH)
            if (pending.isEmpty()) return uploadedBatches to uploadedOperations
            require(pending.all { it.originDeviceId == deviceId }) {
                "Desktop outbox contains an operation from another device. Refusing to batch it under this device identity."
            }

            val batchId = stableBatchId(deviceId, pending)
            val batch = SyncBatch(
                batchId = batchId,
                originDeviceId = deviceId,
                createdAtMs = System.currentTimeMillis(),
                operations = pending
            ).also { it.validate() }
            val encrypted = security.encryptBatch(batch, masterKey)
            val encoded = json.encodeToString(EncryptedSyncPayload.serializer(), encrypted)

            val existingFile = drive.findBatchFile(accessToken, folderId, batchId)
            if (existingFile == null) {
                drive.uploadBatch(accessToken, folderId, batchId, encoded)
            } else {
                // A previous run may have uploaded the file and exited before
                // marking the outbox. Verify exact operations before acknowledging it.
                val previousContent = drive.downloadTextFile(accessToken, existingFile.id)
                val previousPayload = json.decodeFromString(
                    EncryptedSyncPayload.serializer(),
                    previousContent
                )
                val previousBatch = security.decryptBatch(previousPayload, masterKey)
                check(previousBatch.batchId == batchId) {
                    "Existing Drive batch filename contains a different batch ID."
                }
                check(previousBatch.operations.sortedBy { it.operationId } == pending.sortedBy { it.operationId }) {
                    "Existing Drive batch does not match the pending desktop outbox operations."
                }
            }

            store.markUploaded(
                operationIds = pending.map { it.operationId },
                batchId = batchId,
                uploadedAtMs = System.currentTimeMillis()
            )
            uploadedBatches++
            uploadedOperations += pending.size
        }

        return uploadedBatches to uploadedOperations
    }

    private suspend fun downloadBatches(
        accessToken: String,
        folderId: String,
        masterKey: PortableCloudMasterKey
    ): Pair<Int, Pair<Int, Int>> {
        val files = drive.listBatchFiles(accessToken, folderId)
        var downloadedBatches = 0
        var appliedOperations = 0
        var duplicates = 0

        for (file in files) {
            val content = drive.downloadTextFile(accessToken, file.id)
            val encrypted = json.decodeFromString(EncryptedSyncPayload.serializer(), content)
            val batch = security.decryptBatch(encrypted, masterKey)
            val expectedName = "${DesktopDriveVaultClient.BATCH_FILE_PREFIX}${batch.batchId}${DesktopDriveVaultClient.BATCH_FILE_SUFFIX}"
            check(file.name == expectedName) {
                "Drive batch filename does not match its authenticated batch ID."
            }
            val cursor = store.readCursor()
            val result = store.ingestRemoteBatch(
                batch,
                SyncCursor(
                    drivePageToken = cursor.drivePageToken,
                    lastSuccessfulSyncAtMs = System.currentTimeMillis()
                )
            )
            downloadedBatches++
            appliedOperations += result.appliedOperations
            duplicates += result.duplicateOperations
        }

        return downloadedBatches to (appliedOperations to duplicates)
    }

    private fun stableBatchId(deviceId: String, operations: List<SyncOperation>): String {
        require(deviceId.isNotBlank() && operations.isNotEmpty())
        val canonical = buildString {
            append(deviceId)
            append('\n')
            operations.map { it.operationId }.sorted().forEach {
                append(it)
                append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val MAX_OPERATIONS_PER_BATCH = 200
        const val MAX_BATCHES_PER_SYNC = 50
    }
}
