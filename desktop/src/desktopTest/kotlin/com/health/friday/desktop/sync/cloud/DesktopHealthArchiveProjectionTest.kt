package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.repository.HealthArchiveRepository
import com.health.friday.desktop.repository.HealthRepository
import com.health.friday.desktop.repository.NutritionRepository
import com.health.friday.desktop.repository.TasksRepository
import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.HealthRecordSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import com.health.friday.shared.sync.cloud.SyncOperation
import com.health.friday.shared.sync.cloud.SyncOperationType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopHealthArchiveProjectionTest {
    @Test
    fun healthRecordMetadataProjectsAndDeleteTombstoneRemovesIt() = runBlocking {
        val directory = Files.createTempDirectory("friday-health-archive-projection")
        val fileStore = DpapiFileStore(directory.resolve("state.dpapi"), TestProtector())
        val healthArchive = HealthArchiveRepository()
        val applier = DesktopCloudSyncRemoteApplier(
            nutritionRepository = NutritionRepository { },
            healthRepository = HealthRepository { },
            tasksRepository = TasksRepository { },
            healthArchiveRepository = healthArchive
        )
        try {
            val store = DesktopCloudSyncStore(
                deviceId = "desktop-node",
                remoteApplier = applier,
                fileStore = fileStore
            )
            val now = System.currentTimeMillis()
            val archiveId = "android-node:StepsRecord:record-1"
            val payload = HealthRecordSyncPayload(
                archiveId = archiveId,
                sourceDeviceId = "android-node",
                recordType = "StepsRecord",
                recordId = "record-1",
                dataOriginPackage = "com.example.health",
                clientRecordId = null,
                clientRecordVersion = 0L,
                lastModifiedTimeMs = now,
                recordingMethod = 0,
                deviceManufacturer = null,
                deviceModel = null,
                deviceType = null,
                startTimeMs = now - 60_000L,
                endTimeMs = now,
                payloadJson = """{"count":123}"""
            )
            val json = Json { encodeDefaults = true }
            val upsert = SyncOperation(
                operationId = "op-health-record-upsert",
                originDeviceId = "android-node",
                entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                entityId = archiveId,
                operationType = SyncOperationType.UPSERT,
                hlc = HlcTimestamp(now, 1, "android-node").encode(),
                createdAtMs = now,
                payload = json.encodeToJsonElement(HealthRecordSyncPayload.serializer(), payload).jsonObject
            )
            store.ingestRemoteBatch(
                SyncBatch("batch-health-record-upsert", originDeviceId = "android-node", createdAtMs = now, operations = listOf(upsert)),
                SyncCursor(lastSuccessfulSyncAtMs = now)
            )

            assertEquals(1, healthArchive.records.value.size)
            assertEquals("StepsRecord", healthArchive.records.value.single().recordType)
            assertEquals("com.example.health", healthArchive.records.value.single().dataOriginPackage)

            val delete = SyncOperation(
                operationId = "op-health-record-delete",
                originDeviceId = "android-node",
                entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                entityId = archiveId,
                operationType = SyncOperationType.DELETE,
                hlc = HlcTimestamp(now, 2, "android-node").encode(),
                createdAtMs = now + 1L
            )
            store.ingestRemoteBatch(
                SyncBatch("batch-health-record-delete", originDeviceId = "android-node", createdAtMs = now + 1L, operations = listOf(delete)),
                SyncCursor(lastSuccessfulSyncAtMs = now + 1L)
            )

            assertEquals(emptyList(), healthArchive.records.value)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class TestProtector : DpapiProtector {
        override fun protect(plaintext: ByteArray): ByteArray = plaintext.copyOf()
        override fun unprotect(ciphertext: ByteArray): ByteArray = ciphertext.copyOf()
    }
}
