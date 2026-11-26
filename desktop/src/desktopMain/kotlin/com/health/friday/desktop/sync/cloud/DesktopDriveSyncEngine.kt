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
            ?: error(
                "FRIDAY desktop cloud key is not initialized. " +
                    "Restore the recovery key first."
            )

        try {
            val accessToken = oauth.accessToken()

            // Shared prerequisite for both paths.
            val folderId = drive.ensureVaultFolder(accessToken)

            var uploadResult: Pair<Int, Int>? = null
            var downloadResult: Pair<Int, Pair<Int, Int>>? = null

            var uploadFailure: Exception? = null
            var downloadFailure: Exception? = null

            // PATH 1: Upload pending desktop operations.
            try {
                uploadResult = uploadPending(
                    accessToken = accessToken,
                    folderId = folderId,
                    masterKey = masterKey
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                uploadFailure = e
            }

            // PATH 2: Download and apply remote operations.
            // Do not skip this just because uploading failed.
            try {
                downloadResult = downloadBatches(
                    accessToken = accessToken,
                    folderId = folderId,
                    masterKey = masterKey
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadFailure = e
            }

            // Preserve both failures for diagnostics.
            val failure = uploadFailure ?: downloadFailure

            if (failure != null) {
                val secondary = when {
                    failure === uploadFailure -> downloadFailure
                    else -> uploadFailure
                }

                if (secondary != null && secondary !== failure) {
                    failure.addSuppressed(secondary)
                }

                throw failure
            }

            val uploaded = checkNotNull(uploadResult)
            val downloaded = checkNotNull(downloadResult)

            return DesktopDriveSyncResult(
                uploadedBatches = uploaded.first,
                uploadedOperations = uploaded.second,
                downloadedBatches = downloaded.first,
                appliedOperations = downloaded.second.first,
                duplicateOrStaleOperations = downloaded.second.second
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
        val currentCursor = store.readCursor()

        val files: List<com.health.friday.desktop.sync.drive.DesktopDriveFile>
        val nextPageToken: String
        val pageToken = currentCursor.drivePageToken

        if (pageToken.isNullOrBlank()) {
            val startToken = drive.fetchStartPageToken(accessToken)
            files = drive.listBatchFiles(accessToken, folderId)
            nextPageToken = startToken
        } else {
            val changesResult = try {
                drive.fetchChanges(accessToken, pageToken, folderId)
            } catch (e: Exception) {
                if (e.message?.contains("410") == true || e.message?.contains("pageToken") == true) {
                    val startToken = drive.fetchStartPageToken(accessToken)
                    val baselineFiles = drive.listBatchFiles(accessToken, folderId)
                    com.health.friday.desktop.sync.drive.DesktopChangesResult(baselineFiles, startToken)
                } else {
                    throw e
                }
            }
            files = changesResult.newFiles
            nextPageToken = changesResult.newPageToken
        }

        var downloadedBatches = 0
        var appliedOperations = 0
        var duplicates = 0

        val uniqueFiles = files
            .filter {
                it.name.startsWith(com.health.friday.desktop.sync.drive.DesktopDriveVaultClient.BATCH_FILE_PREFIX) &&
                    it.name.endsWith(com.health.friday.desktop.sync.drive.DesktopDriveVaultClient.BATCH_FILE_SUFFIX)
            }
            .distinctBy { it.id }

        for (file in uniqueFiles) {
            val content = drive.downloadTextFile(accessToken, file.id)
            val encrypted = json.decodeFromString(EncryptedSyncPayload.serializer(), content)
            val batch = security.decryptBatch(encrypted, masterKey)
            batch.validate()

            val expectedName = "${com.health.friday.desktop.sync.drive.DesktopDriveVaultClient.BATCH_FILE_PREFIX}${batch.batchId}${com.health.friday.desktop.sync.drive.DesktopDriveVaultClient.BATCH_FILE_SUFFIX}"
            check(file.name == expectedName) {
                "Drive batch filename does not match its authenticated batch ID."
            }

            val result = store.ingestRemoteBatch(
                batch,
                SyncCursor(
                    drivePageToken = currentCursor.drivePageToken,
                    lastSuccessfulSyncAtMs = System.currentTimeMillis()
                )
            )
            downloadedBatches++
            appliedOperations += result.appliedOperations
            duplicates += result.duplicateOperations
        }

        store.saveCursor(
            SyncCursor(
                drivePageToken = nextPageToken,
                lastSuccessfulSyncAtMs = System.currentTimeMillis()
            )
        )

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
