package com.health.friday.desktop.sync.cloud

import com.health.friday.shared.protocol.ClockDriftException
import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncStore
import com.health.friday.shared.sync.cloud.RemoteBatchResult
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.charset.StandardCharsets

@Serializable
private data class DesktopOutboxRow(
    val operation: SyncOperation,
    val status: String = "PENDING",
    val batchId: String? = null,
    val uploadedAtMs: Long? = null
) {
    companion object {
        const val PENDING = "PENDING"
        const val UPLOADED = "UPLOADED"
    }
}

@Serializable
private data class DesktopInboxRow(
    val operationId: String,
    val batchId: String,
    val originDeviceId: String,
    val processedAtMs: Long
)

@Serializable
data class DesktopCloudSyncEntityRecord(
    val entityType: String,
    val entityId: String,
    val hlc: String,
    val isDeleted: Boolean,
    /** Canonical JSON object from SyncOperation.payload; null for tombstones. */
    val payloadJson: String?
)

@Serializable
private data class DesktopCursorState(
    val drivePageToken: String? = null,
    val lastSuccessfulSyncAtMs: Long? = null
) {
    fun toCursor() = SyncCursor(drivePageToken, lastSuccessfulSyncAtMs)
}

@Serializable
private data class DesktopCloudSyncDiskState(
    val formatVersion: Int = 1,
    val outbox: List<DesktopOutboxRow> = emptyList(),
    val inbox: List<DesktopInboxRow> = emptyList(),
    val entities: List<DesktopCloudSyncEntityRecord> = emptyList(),
    val cursor: DesktopCursorState = DesktopCursorState(),
    val clockWallMs: Long = 0L,
    val clockCounter: Int = 0
)

/**
 * DPAPI-protected desktop implementation of the shared CloudSyncStore contract.
 * Outbox, inbox, entity versions/tombstones, cursor and HLC state are committed
 * together in one encrypted file replacement.
 */
class DesktopCloudSyncStore internal constructor(
    private val deviceId: String,
    private val remoteApplier: DesktopCloudSyncRemoteApplier? = null,
    private val fileStore: DpapiFileStore = DpapiFileStore(
        DpapiFileStore.appDataFile(STATE_FILE_NAME)
    ),
    private val onOperationEnqueued: (() -> Unit)? = null
) : CloudSyncStore {
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(deviceId.isNotBlank()) { "Desktop cloud sync device ID must not be blank." }
        // Hydrate the desktop repositories from the last committed cloud snapshot.
        remoteApplier?.publishSnapshot(snapshotEntities())
    }

    override suspend fun enqueue(operation: SyncOperation) {
        operation.validate()
        require(operation.originDeviceId == deviceId) {
            "Desktop outbox accepts only operations created by this installation."
        }

        val incomingClock = HlcTimestamp.parse(operation.hlc)
            ?: throw IllegalArgumentException("Malformed local operation HLC: ${operation.hlc}")

        val committedSnapshot = synchronized(lock) {
            val current = readState()
            val existingOutbox = current.outbox.firstOrNull {
                it.operation.operationId == operation.operationId
            }
            if (existingOutbox != null) {
                require(existingOutbox.operation == operation) {
                    "Operation ID collision: ${operation.operationId} refers to different content."
                }
            }

            val incomingRecord = DesktopCloudSyncRemoteApplier.recordFrom(operation)
            val key = entityKey(operation.entityType, operation.entityId)
            val previous = current.entities.firstOrNull {
                it.entityType == operation.entityType && it.entityId == operation.entityId
            }
            val previousClock = previous?.let {
                HlcTimestamp.parse(it.hlc)
                    ?: throw IllegalStateException("Malformed stored HLC for ${it.entityType}/${it.entityId}.")
            }
            val shouldUpdateEntity = previousClock == null || incomingClock > previousClock

            // New local mutations must be newer than the committed entity version.
            // An already-enqueued retry may be older because a later edit has since won.
            require(shouldUpdateEntity || existingOutbox != null) {
                "Refusing stale local operation ${operation.operationId} for ${operation.entityType}/${operation.entityId}."
            }

            val mergedClockState = mergeClock(current, incomingClock)
            val nextOutbox = if (existingOutbox == null) {
                current.outbox + DesktopOutboxRow(operation)
            } else {
                current.outbox
            }
            val nextEntities = if (shouldUpdateEntity) {
                (current.entities.filterNot { entityKey(it.entityType, it.entityId) == key } + incomingRecord)
                    .sortedWith(compareBy<DesktopCloudSyncEntityRecord> { it.entityType }.thenBy { it.entityId })
            } else {
                current.entities
            }

            val updated = mergedClockState.copy(
                outbox = nextOutbox,
                entities = nextEntities
            )
            if (updated != current) writeState(updated)
            updated.entities
        }

        // Persist the local mutation and its entity projection before publishing it.
        // This keeps newly created/edited records visible after an offline restart.
        remoteApplier?.publishSnapshot(committedSnapshot)
        onOperationEnqueued?.invoke()
    }
    override suspend fun pendingOutbox(limit: Int): List<SyncOperation> {
        require(limit > 0) { "limit must be greater than zero." }
        return synchronized(lock) {
            readState().outbox
                .asSequence()
                .filter { it.status == "PENDING" }
                .map { it.operation }
                .sortedWith(compareBy<SyncOperation> { it.createdAtMs }.thenBy { it.hlc }.thenBy { it.operationId })
                .take(limit)
                .toList()
        }
    }

    override suspend fun markUploaded(
        operationIds: List<String>,
        batchId: String,
        uploadedAtMs: Long
    ) {
        require(operationIds.isNotEmpty()) { "operationIds cannot be empty." }
        require(operationIds.distinct().size == operationIds.size) { "operationIds contains duplicates." }
        require(batchId.isNotBlank()) { "batchId must not be blank." }
        require(uploadedAtMs >= 0L) { "uploadedAtMs must be non-negative." }

        synchronized(lock) {
            val current = readState()
            val requested = operationIds.toSet()
            val found = current.outbox.count { it.operation.operationId in requested }
            require(found == requested.size) {
                "Cannot mark an outbox operation uploaded because one or more IDs are missing."
            }
            val updated = current.outbox.map { row ->
                if (row.operation.operationId !in requested) row
                else {
                    require(row.status == "PENDING" ||
                        (row.status == DesktopOutboxRow.UPLOADED && row.batchId == batchId)) {
                        "Outbox operation ${row.operation.operationId} was already assigned to a different batch."
                    }
                    row.copy(
                        status = "UPLOADED",
                        batchId = batchId,
                        uploadedAtMs = uploadedAtMs
                    )
                }
            }
            writeState(current.copy(outbox = updated))
        }
    }

    override suspend fun readCursor(): SyncCursor = synchronized(lock) {
        readState().cursor.toCursor()
    }

    override suspend fun ingestRemoteBatch(
        batch: SyncBatch,
        nextCursor: SyncCursor
    ): RemoteBatchResult {
        batch.validate()
        val syncTime = nextCursor.lastSuccessfulSyncAtMs
        require(syncTime == null || syncTime >= 0L) {
            "Cursor timestamp must be non-negative."
        }

        val resultAndSnapshot = synchronized(lock) {
            var current = readState()
            val inboxIds = current.inbox.asSequence().map { it.operationId }.toMutableSet()
            val entityMap = current.entities.associateBy { entityKey(it.entityType, it.entityId) }.toMutableMap()
            val newInbox = current.inbox.toMutableList()
            var applied = 0
            var duplicates = 0

            for (operation in batch.operations) {
                operation.validate()
                if (!inboxIds.add(operation.operationId)) {
                    duplicates++
                    continue
                }

                val incomingClock = HlcTimestamp.parse(operation.hlc)
                    ?: throw IllegalArgumentException("Malformed HLC timestamp in ${operation.operationId}.")
                current = mergeClock(current, incomingClock)

                val key = entityKey(operation.entityType, operation.entityId)
                val previous = entityMap[key]
                val incomingRecord = DesktopCloudSyncRemoteApplier.recordFrom(operation)
                val previousClock = previous?.let {
                    HlcTimestamp.parse(it.hlc)
                        ?: throw IllegalStateException("Malformed stored HLC for ${it.entityType}/${it.entityId}.")
                }

                if (previousClock == null || incomingClock > previousClock) {
                    entityMap[key] = incomingRecord
                    applied++
                } else {
                    duplicates++
                }

                newInbox += DesktopInboxRow(
                    operationId = operation.operationId,
                    batchId = batch.batchId,
                    originDeviceId = operation.originDeviceId,
                    processedAtMs = System.currentTimeMillis()
                )
            }

            val updated = current.copy(
                inbox = newInbox,
                entities = entityMap.values.sortedWith(compareBy<DesktopCloudSyncEntityRecord> { it.entityType }.thenBy { it.entityId }),
                cursor = DesktopCursorState(
                    drivePageToken = nextCursor.drivePageToken,
                    lastSuccessfulSyncAtMs = nextCursor.lastSuccessfulSyncAtMs
                )
            )
            writeState(updated)
            RemoteBatchResult(appliedOperations = applied, duplicateOperations = duplicates) to updated.entities
        }

        // The encrypted disk snapshot is authoritative. UI flows are projections
        // and are refreshed only after the snapshot, inbox and cursor are committed.
        remoteApplier?.publishSnapshot(resultAndSnapshot.second)
        return resultAndSnapshot.first
    }

    /** Returns the committed entity/tombstone snapshot for repository hydration. */
    fun snapshotEntities(): List<DesktopCloudSyncEntityRecord> = synchronized(lock) {
        readState().entities
    }

    /** Generate and durably store the next local HLC before returning it. */
    fun nextHlc(): String = synchronized(lock) {
        val current = readState()
        val now = System.currentTimeMillis()
        var wall = current.clockWallMs
        var counter = current.clockCounter
        if (now > wall) {
            wall = now
            counter = 0
        } else {
            counter++
            if (counter > MAX_HLC_COUNTER) {
                wall++
                counter = 0
            }
        }
        val updated = current.copy(clockWallMs = wall, clockCounter = counter)
        writeState(updated)
        HlcTimestamp(wall, counter, deviceId).encode()
    }

    suspend fun enqueueLocalUpsert(
        entityType: String,
        entityId: String,
        payload: JsonObject,
        createdAtMs: Long = System.currentTimeMillis()
    ): SyncOperation {
        val operation = SyncOperation.newUpsert(
            deviceId = deviceId,
            entityType = entityType,
            entityId = entityId,
            hlc = nextHlc(),
            createdAtMs = createdAtMs,
            payload = payload
        )
        enqueue(operation)
        return operation
    }

    suspend fun enqueueLocalDelete(
        entityType: String,
        entityId: String,
        createdAtMs: Long = System.currentTimeMillis()
    ): SyncOperation {
        val operation = SyncOperation.newDelete(
            deviceId = deviceId,
            entityType = entityType,
            entityId = entityId,
            hlc = nextHlc(),
            createdAtMs = createdAtMs
        )
        enqueue(operation)
        return operation
    }

    private fun mergeClock(
        state: DesktopCloudSyncDiskState,
        remote: HlcTimestamp
    ): DesktopCloudSyncDiskState {
        val now = System.currentTimeMillis()
        if (remote.wallMs - now > MAX_CLOCK_DRIFT_MS) {
            throw ClockDriftException(
                "Remote clock is ${(remote.wallMs - now) / 1000}s ahead of this PC; " +
                    "the allowed drift is ${MAX_CLOCK_DRIFT_MS / 1000}s."
            )
        }
        val newWall = maxOf(state.clockWallMs, remote.wallMs, now)
        var newCounter = when {
            newWall == state.clockWallMs && newWall == remote.wallMs -> maxOf(state.clockCounter, remote.counter) + 1
            newWall == state.clockWallMs -> state.clockCounter + 1
            newWall == remote.wallMs -> remote.counter + 1
            else -> 0
        }
        var finalWall = newWall
        if (newCounter > MAX_HLC_COUNTER) {
            finalWall++
            newCounter = 0
        }
        return state.copy(clockWallMs = finalWall, clockCounter = newCounter)
    }

    private fun readState(): DesktopCloudSyncDiskState {
        val plaintext = fileStore.read() ?: return DesktopCloudSyncDiskState()
        try {
            require(plaintext.size <= MAX_STATE_BYTES) {
                "FRIDAY desktop cloud-sync state exceeds the supported size limit."
            }
            val decoded = runCatching {
                json.decodeFromString<DesktopCloudSyncDiskState>(
                    String(plaintext, StandardCharsets.UTF_8)
                )
            }.getOrElse { e ->
                System.err.println("[DesktopCloudSyncStore] Saved state corrupted or unparseable: ${e.message}")
                val freshState = DesktopCloudSyncDiskState()
                runCatching { writeState(freshState) }
                return freshState
            }
            require(decoded.formatVersion == STATE_FORMAT_VERSION) {
                "Unsupported FRIDAY desktop cloud-sync state version: ${decoded.formatVersion}."
            }
            require(decoded.clockWallMs >= 0L && decoded.clockCounter in 0..MAX_HLC_COUNTER) {
                "Invalid persisted desktop HLC state."
            }
            return decoded
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeState(state: DesktopCloudSyncDiskState) {
        val plaintext = json.encodeToString(state).toByteArray(StandardCharsets.UTF_8)
        try {
            require(plaintext.size <= MAX_STATE_BYTES) {
                "FRIDAY desktop cloud-sync state exceeds the supported size limit."
            }
            fileStore.write(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun entityKey(type: String, id: String): String = "$type\u0000$id"

    private companion object {
        const val STATE_FILE_NAME = "cloud-sync-state.dpapi"
        const val STATE_FORMAT_VERSION = 1
        const val MAX_STATE_BYTES = 256 * 1024 * 1024
        const val MAX_HLC_COUNTER = 0xFFFF
        const val MAX_CLOCK_DRIFT_MS = 60L * 60L * 1000L
    }
}
