package com.health.friday.sync.cloud

import androidx.room.withTransaction
import com.health.friday.data.local.CloudSyncDao
import com.health.friday.data.local.CloudSyncInboxEntity
import com.health.friday.data.local.CloudSyncOutboxEntity
import com.health.friday.data.local.CloudSyncCursorEntity
import com.health.friday.data.local.FridayDatabase
import com.health.friday.shared.sync.cloud.CloudSyncStore
import com.health.friday.shared.sync.cloud.RemoteBatchResult
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Applies a remote operation to FRIDAY's actual local data.
 *
 * Implementations will be added for meals, water, tasks, goals and journal.
 *
 * This callback is invoked inside the same Room transaction that records
 * the inbox ID and advances the Drive cursor.
 */
fun interface CloudSyncRemoteApplier {
    suspend fun apply(operation: SyncOperation)
}

class AndroidCloudSyncStore(
    private val database: FridayDatabase,
    private val dao: CloudSyncDao,
    private val remoteApplier: CloudSyncRemoteApplier,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : CloudSyncStore {

    override suspend fun enqueue(operation: SyncOperation) {
        operation.validate()

        dao.insertOutbox(
            CloudSyncOutboxEntity(
                operationId = operation.operationId,
                originDeviceId = operation.originDeviceId,
                entityType = operation.entityType,
                entityId = operation.entityId,
                operationType = operation.operationType.name,
                hlc = operation.hlc,
                createdAtMs = operation.createdAtMs,
                schemaVersion = operation.schemaVersion,
                payloadJson = operation.payload?.let {
                    json.encodeToString(JsonObject.serializer(), it)
                }
            )
        )
    }

    override suspend fun pendingOutbox(
        limit: Int
    ): List<SyncOperation> {
        require(limit > 0) {
            "limit must be greater than zero"
        }

        return dao.getPendingOutbox(limit).map { row ->
            val operationType = runCatching {
                com.health.friday.shared.sync.cloud.SyncOperationType.valueOf(
                    row.operationType
                )
            }.getOrElse {
                throw IllegalStateException(
                    "Unknown operation type '${row.operationType}' " +
                        "for operation ${row.operationId}",
                    it
                )
            }

            val payload = row.payloadJson?.let { raw ->
                runCatching {
                    json.parseToJsonElement(raw).jsonObject
                }.getOrElse {
                    throw IllegalStateException(
                        "Invalid JSON payload for operation ${row.operationId}",
                        it
                    )
                }
            }

            SyncOperation(
                operationId = row.operationId,
                originDeviceId = row.originDeviceId,
                entityType = row.entityType,
                entityId = row.entityId,
                operationType = operationType,
                hlc = row.hlc,
                createdAtMs = row.createdAtMs,
                schemaVersion = row.schemaVersion,
                payload = payload
            ).also { it.validate() }
        }
    }

    override suspend fun markUploaded(
        operationIds: List<String>,
        batchId: String,
        uploadedAtMs: Long
    ) {
        require(operationIds.isNotEmpty()) {
            "operationIds cannot be empty"
        }
        require(operationIds.distinct().size == operationIds.size) {
            "operationIds contains duplicates"
        }
        require(batchId.isNotBlank()) {
            "batchId must not be blank"
        }
        require(uploadedAtMs >= 0L) {
            "uploadedAtMs must be non-negative"
        }

        dao.markUploaded(
            operationIds = operationIds,
            batchId = batchId,
            uploadedAtMs = uploadedAtMs
        )
    }

    override suspend fun readCursor(): SyncCursor {
        val row = dao.getCursor() ?: return SyncCursor()

        return SyncCursor(
            drivePageToken = row.drivePageToken,
            lastSuccessfulSyncAtMs = row.lastSuccessfulSyncAtMs
        )
    }

    override suspend fun ingestRemoteBatch(
        batch: SyncBatch,
        nextCursor: SyncCursor
    ): RemoteBatchResult {
        batch.validate()

        val syncTime = nextCursor.lastSuccessfulSyncAtMs
        require(syncTime == null || syncTime >= 0L) {
            "Cursor timestamp must be non-negative"
        }

        var applied = 0
        var duplicates = 0

        database.withTransaction {
            for (operation in batch.operations) {
                val inserted = dao.insertInbox(
                    CloudSyncInboxEntity(
                        operationId = operation.operationId,
                        batchId = batch.batchId,
                        originDeviceId = operation.originDeviceId,
                        processedAtMs = System.currentTimeMillis()
                    )
                )

                if (inserted == -1L) {
                    duplicates++
                    continue
                }

                // If this fails, the transaction rolls back:
                // inbox insertion and cursor update are not committed.
                remoteApplier.apply(operation)
                applied++
            }

            dao.saveCursor(
                CloudSyncCursorEntity(
                    id = 1,
                    drivePageToken = nextCursor.drivePageToken,
                    lastSuccessfulSyncAtMs =
                        nextCursor.lastSuccessfulSyncAtMs
                )
            )
        }

        return RemoteBatchResult(
            appliedOperations = applied,
            duplicateOperations = duplicates
        )
    }
}
