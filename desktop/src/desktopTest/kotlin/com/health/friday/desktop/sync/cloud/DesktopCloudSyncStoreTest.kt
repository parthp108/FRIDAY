package com.health.friday.desktop.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import com.health.friday.shared.sync.cloud.SyncOperation
import com.health.friday.shared.sync.cloud.SyncOperationType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCloudSyncStoreTest {

    @Test
    fun outboxSurvivesRestartAndUploadAcknowledgement() = withStore { store, fileStore ->
        val operation = mealUpsert("desktop-node", HlcTimestamp(1_700_000_000_000, 0, "desktop-node").encode(), "op-1")
        store.enqueue(operation)
        assertEquals(listOf(operation), store.pendingOutbox(10))

        val reopened = DesktopCloudSyncStore("desktop-node", fileStore = fileStore)
        assertEquals(listOf(operation), reopened.pendingOutbox(10))
        reopened.markUploaded(listOf("op-1"), "batch-1", 1_700_000_000_001)
        assertTrue(reopened.pendingOutbox(10).isEmpty())
    }

    @Test
    fun remoteInboxAndCursorAreCommittedAndDeduplicated() = withStore { store, _ ->
        val operation = mealUpsert("android-node", HlcTimestamp(1_700_000_000_000, 0, "android-node").encode(), "op-remote")
        val batch = SyncBatch("batch-remote", originDeviceId = "android-node", createdAtMs = 1_700_000_000_000, operations = listOf(operation))
        val cursor = SyncCursor(lastSuccessfulSyncAtMs = 1_700_000_000_100)

        val first = store.ingestRemoteBatch(batch, cursor)
        val second = store.ingestRemoteBatch(batch, cursor)

        assertEquals(1, first.appliedOperations)
        assertEquals(0, first.duplicateOperations)
        assertEquals(0, second.appliedOperations)
        assertEquals(1, second.duplicateOperations)
        assertEquals(cursor.lastSuccessfulSyncAtMs, store.readCursor().lastSuccessfulSyncAtMs)
        assertEquals("meal-id-1", store.snapshotEntities().single().entityId)
    }

    @Test
    fun staleRemoteOperationCannotReplaceNewerEntityVersion() = withStore { store, _ ->
        val wall = 1_700_000_000_000L
        val newer = mealUpsert("android-node", HlcTimestamp(wall, 8, "android-node").encode(), "op-new")
        val older = mealUpsert("android-node", HlcTimestamp(wall, 3, "android-node").encode(), "op-old", name = "Old name")
        store.ingestRemoteBatch(
            SyncBatch("batch-new", originDeviceId = "android-node", createdAtMs = wall, operations = listOf(newer)),
            SyncCursor(lastSuccessfulSyncAtMs = wall)
        )
        val result = store.ingestRemoteBatch(
            SyncBatch("batch-old", originDeviceId = "android-node", createdAtMs = wall, operations = listOf(older)),
            SyncCursor(lastSuccessfulSyncAtMs = wall + 1)
        )

        assertEquals(0, result.appliedOperations)
        assertEquals(1, result.duplicateOperations)
        assertEquals(HlcTimestamp(wall, 8, "android-node").encode(), store.snapshotEntities().single().hlc)
    }

    @Test
    fun malformedDailySignalsDeleteIsRejectedBeforeCommit() = withStore { store, _ ->
        val wall = 1_700_000_000_000L
        val delete = SyncOperation(
            operationId = "op-invalid-delete",
            originDeviceId = "android-node",
            entityType = CloudSyncEntityTypes.DAILY_SIGNALS,
            entityId = "android-node:not-a-date",
            operationType = SyncOperationType.DELETE,
            hlc = HlcTimestamp(wall, 1, "android-node").encode(),
            createdAtMs = wall
        )
        val batch = SyncBatch(
            "batch-invalid-delete",
            originDeviceId = "android-node",
            createdAtMs = wall,
            operations = listOf(delete)
        )
        assertFailsWith<IllegalArgumentException> {
            runBlocking { store.ingestRemoteBatch(batch, SyncCursor(lastSuccessfulSyncAtMs = wall)) }
        }
        assertTrue(store.snapshotEntities().isEmpty())
        assertEquals(null, store.readCursor().lastSuccessfulSyncAtMs)
    }

    @Test
    fun deleteIsPersistedAsTombstone() = withStore { store, fileStore ->
        val wall = 1_700_000_000_000L
        val delete = SyncOperation(
            operationId = "op-delete",
            originDeviceId = "android-node",
            entityType = CloudSyncEntityTypes.MEAL,
            entityId = "meal-id-1",
            operationType = SyncOperationType.DELETE,
            hlc = HlcTimestamp(wall, 4, "android-node").encode(),
            createdAtMs = wall
        )
        store.ingestRemoteBatch(
            SyncBatch("batch-delete", originDeviceId = "android-node", createdAtMs = wall, operations = listOf(delete)),
            SyncCursor(lastSuccessfulSyncAtMs = wall)
        )

        val reopened = DesktopCloudSyncStore("desktop-node", fileStore = fileStore)
        val tombstone = reopened.snapshotEntities().single()
        assertTrue(tombstone.isDeleted)
        assertEquals(null, tombstone.payloadJson)
        assertNotNull(tombstone.hlc)
    }

    private fun mealUpsert(
        deviceId: String,
        hlc: String,
        operationId: String,
        name: String = "Meal"
    ) = SyncOperation(
        operationId = operationId,
        originDeviceId = deviceId,
        entityType = CloudSyncEntityTypes.MEAL,
        entityId = "meal-id-1",
        operationType = SyncOperationType.UPSERT,
        hlc = hlc,
        createdAtMs = 1_700_000_000_000,
        payload = buildJsonObject {
            put("syncId", "meal-id-1")
            put("name", name)
            put("mealType", "lunch")
            put("calories", 500)
            put("protein", 25.0)
            put("carbohydrates", 60.0)
            put("fat", 15.0)
            put("timestamp", 1_700_000_000_000)
            put("isEstimated", false)
            put("nutrientsJson", "{}")
        }
    )

    private fun withStore(block: suspend (DesktopCloudSyncStore, DpapiFileStore) -> Unit) {
        val directory = Files.createTempDirectory("friday-desktop-cloud-sync")
        val fileStore = DpapiFileStore(directory.resolve("state.dpapi"), TestDpapiProtector())
        val store = DesktopCloudSyncStore("desktop-node", fileStore = fileStore)
        try {
            runBlocking { block(store, fileStore) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class TestDpapiProtector : DpapiProtector {
        override fun protect(plaintext: ByteArray): ByteArray = plaintext.copyOf()
        override fun unprotect(ciphertext: ByteArray): ByteArray = ciphertext.copyOf()
    }
}
