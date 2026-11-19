package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.repository.HealthRepository
import com.health.friday.desktop.repository.NutritionRepository
import com.health.friday.desktop.repository.TasksRepository
import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import com.health.friday.shared.sync.cloud.JournalSyncPayload
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

class DesktopGoalJournalProjectionTest {
    @Test
    fun remoteGoalAndJournalEntriesReachDesktopRepositoryFlows() = runBlocking {
        val directory = Files.createTempDirectory("friday-goal-journal-projection")
        val fileStore = DpapiFileStore(directory.resolve("state.dpapi"), TestProtector())
        val tasks = TasksRepository { }
        val applier = DesktopCloudSyncRemoteApplier(
            nutritionRepository = NutritionRepository { },
            healthRepository = HealthRepository { },
            tasksRepository = tasks
        )
        try {
            val store = DesktopCloudSyncStore(
                deviceId = "desktop-node",
                remoteApplier = applier,
                fileStore = fileStore
            )
            val now = System.currentTimeMillis()
            val json = Json { encodeDefaults = true }
            val goal = GoalSyncPayload(
                syncId = "goal-remote-1",
                title = "Finish project",
                targetDate = null,
                isDone = false,
                createdAt = now
            )
            val journal = JournalSyncPayload(
                syncId = "journal-remote-1",
                text = "A useful reflection",
                feedback = null,
                createdAt = now
            )
            val operations = listOf(
                SyncOperation(
                    operationId = "op-goal-remote-1",
                    originDeviceId = "android-node",
                    entityType = CloudSyncEntityTypes.GOAL,
                    entityId = goal.syncId,
                    operationType = SyncOperationType.UPSERT,
                    hlc = HlcTimestamp(now, 1, "android-node").encode(),
                    createdAtMs = now,
                    payload = json.encodeToJsonElement(GoalSyncPayload.serializer(), goal).jsonObject
                ),
                SyncOperation(
                    operationId = "op-journal-remote-1",
                    originDeviceId = "android-node",
                    entityType = CloudSyncEntityTypes.JOURNAL_ENTRY,
                    entityId = journal.syncId,
                    operationType = SyncOperationType.UPSERT,
                    hlc = HlcTimestamp(now, 2, "android-node").encode(),
                    createdAtMs = now,
                    payload = json.encodeToJsonElement(JournalSyncPayload.serializer(), journal).jsonObject
                )
            )
            val batch = SyncBatch(
                batchId = "batch-goal-journal-projection",
                originDeviceId = "android-node",
                createdAtMs = now,
                operations = operations
            )

            store.ingestRemoteBatch(batch, SyncCursor(lastSuccessfulSyncAtMs = now))

            assertEquals(listOf(goal), tasks.goals.value)
            assertEquals(listOf(journal), tasks.journalEntries.value)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class TestProtector : DpapiProtector {
        override fun protect(plaintext: ByteArray): ByteArray = plaintext.copyOf()
        override fun unprotect(ciphertext: ByteArray): ByteArray = ciphertext.copyOf()
    }
}