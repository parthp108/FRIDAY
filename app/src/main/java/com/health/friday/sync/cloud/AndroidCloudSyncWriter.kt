package com.health.friday.sync.cloud

import androidx.room.withTransaction
import com.health.friday.data.local.CloudSyncDao
import com.health.friday.data.local.CloudSyncOutboxEntity
import com.health.friday.data.local.FridayDatabase
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class CloudSyncMutation<T>(
    val value: T,
    val payload: JsonObject
)

class AndroidCloudSyncWriter(
    private val database: FridayDatabase,
    private val dao: CloudSyncDao,
    private val clock: AndroidHybridLogicalClock,
    val deviceId: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Local entity mutation + HLC update + UPSERT outbox
     * insertion are one Room transaction.
     */
    suspend fun <T> upsert(
        entityType: String,
        entityId: String,
        mutation: suspend (hlc: String) -> CloudSyncMutation<T>
    ): T {
        require(entityType.isNotBlank())
        require(entityId.isNotBlank())

        return database.withTransaction {
            val timestamp = clock.nextInTransaction()
            val encodedHlc = timestamp.encode()

            val result = mutation(encodedHlc)

            val operation = SyncOperation.newUpsert(
                deviceId = deviceId,
                entityType = entityType,
                entityId = entityId,
                hlc = encodedHlc,
                createdAtMs = timestamp.wallMs,
                payload = result.payload
            )

            insertOperation(operation)
            result.value
        }
    }

    /**
     * Local deletion + HLC update + DELETE tombstone
     * insertion are one Room transaction.
     */
    suspend fun <T> delete(
        entityType: String,
        entityId: String,
        deleteLocal: suspend () -> T
    ): T {
        require(entityType.isNotBlank())
        require(entityId.isNotBlank())

        return database.withTransaction {
            val timestamp = clock.nextInTransaction()

            val result = deleteLocal()

            val operation = SyncOperation.newDelete(
                deviceId = deviceId,
                entityType = entityType,
                entityId = entityId,
                hlc = timestamp.encode(),
                createdAtMs = timestamp.wallMs
            )

            insertOperation(operation)
            result
        }
    }

    private suspend fun insertOperation(
        operation: SyncOperation
    ) {
        operation.validate()

        val inserted = dao.insertOutbox(
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

        check(inserted != -1L) {
            "Cloud sync operation ID collision: ${operation.operationId}"
        }
    }
}
