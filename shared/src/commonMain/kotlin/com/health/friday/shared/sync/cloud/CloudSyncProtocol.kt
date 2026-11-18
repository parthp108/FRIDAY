package com.health.friday.shared.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.protocol.Ids
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val CLOUD_SYNC_PROTOCOL_VERSION = 1

@Serializable
enum class SyncOperationType {
    UPSERT,
    DELETE
}

/**
 * A single change made on one FRIDAY device.
 *
 * entityId must be a globally unique record ID, NOT a Room or SQLite
 * auto-increment primary key.
 */
@Serializable
data class SyncOperation(
    val operationId: String,
    val originDeviceId: String,
    val entityType: String,
    val entityId: String,
    val operationType: SyncOperationType,
    val hlc: String,
    val createdAtMs: Long,
    val schemaVersion: Int = 1,
    val payload: JsonObject? = null
) {
    fun validate() {
        require(operationId.isNotBlank()) {
            "operationId must not be blank"
        }
        require(originDeviceId.isNotBlank()) {
            "originDeviceId must not be blank"
        }
        require(entityType.isNotBlank()) {
            "entityType must not be blank"
        }
        require(entityId.isNotBlank()) {
            "entityId must not be blank"
        }
        require(createdAtMs >= 0L) {
            "createdAtMs must be non-negative"
        }
        require(schemaVersion > 0) {
            "schemaVersion must be positive"
        }

        val timestamp = HlcTimestamp.parse(hlc)
            ?: throw IllegalArgumentException("Malformed HLC timestamp")

        require(timestamp.nodeId == originDeviceId) {
            "HLC nodeId must match originDeviceId"
        }

        when (operationType) {
            SyncOperationType.UPSERT -> require(payload != null) {
                "UPSERT requires a payload"
            }

            SyncOperationType.DELETE -> require(payload == null) {
                "DELETE must not contain a payload"
            }
        }
    }

    companion object {

        fun newUpsert(
            deviceId: String,
            entityType: String,
            entityId: String,
            hlc: String,
            createdAtMs: Long,
            payload: JsonObject,
            schemaVersion: Int = 1
        ): SyncOperation {
            return SyncOperation(
                operationId = Ids.randomHex(16),
                originDeviceId = deviceId,
                entityType = entityType,
                entityId = entityId,
                operationType = SyncOperationType.UPSERT,
                hlc = hlc,
                createdAtMs = createdAtMs,
                schemaVersion = schemaVersion,
                payload = payload
            ).also { it.validate() }
        }

        fun newDelete(
            deviceId: String,
            entityType: String,
            entityId: String,
            hlc: String,
            createdAtMs: Long,
            schemaVersion: Int = 1
        ): SyncOperation {
            return SyncOperation(
                operationId = Ids.randomHex(16),
                originDeviceId = deviceId,
                entityType = entityType,
                entityId = entityId,
                operationType = SyncOperationType.DELETE,
                hlc = hlc,
                createdAtMs = createdAtMs,
                schemaVersion = schemaVersion,
                payload = null
            ).also { it.validate() }
        }
    }
}

/**
 * Immutable group of operations uploaded to Google Drive.
 */
@Serializable
data class SyncBatch(
    val batchId: String,
    val protocolVersion: Int = CLOUD_SYNC_PROTOCOL_VERSION,
    val originDeviceId: String,
    val createdAtMs: Long,
    val operations: List<SyncOperation>
) {
    fun validate() {
        require(batchId.isNotBlank()) {
            "batchId must not be blank"
        }
        require(protocolVersion == CLOUD_SYNC_PROTOCOL_VERSION) {
            "Unsupported cloud sync protocol version: $protocolVersion"
        }
        require(originDeviceId.isNotBlank()) {
            "originDeviceId must not be blank"
        }
        require(createdAtMs >= 0L) {
            "createdAtMs must be non-negative"
        }
        require(operations.isNotEmpty()) {
            "Sync batch cannot be empty"
        }
        require(
            operations.map { it.operationId }.distinct().size == operations.size
        ) {
            "Duplicate operation IDs in batch"
        }

        operations.forEach { operation ->
            operation.validate()

            require(operation.originDeviceId == originDeviceId) {
                "Operation origin does not match batch origin"
            }
        }
    }

    companion object {
        fun create(
            deviceId: String,
            createdAtMs: Long,
            operations: List<SyncOperation>
        ): SyncBatch {
            return SyncBatch(
                batchId = Ids.randomHex(16),
                originDeviceId = deviceId,
                createdAtMs = createdAtMs,
                operations = operations
            ).also { it.validate() }
        }
    }
}

/**
 * Google Drive Changes API checkpoint.
 *
 * pageToken is opaque. Never parse or modify it.
 */
@Serializable
data class SyncCursor(
    val drivePageToken: String? = null,
    val lastSuccessfulSyncAtMs: Long? = null
)
