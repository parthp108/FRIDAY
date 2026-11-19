package com.health.friday.sync.cloud

import com.health.friday.data.local.FridayDatabase
import com.health.friday.device.toCloudSyncPayload
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes

/**
 * One-time, idempotent enqueue of rows that existed before CloudSyncWriter
 * was installed. Rows with a non-null syncHlc already have a corresponding
 * writer mutation, so they are not enqueued again.
 */
class CloudSyncBackfill(
    private val database: FridayDatabase,
    private val writer: AndroidCloudSyncWriter
) {
    suspend fun run(): CloudSyncBackfillReport {
        val mealCount = enqueueMeals()
        val waterCount = enqueueWater()
        val todoCount = enqueueTodos()
        val goalCount = enqueueGoals()
        val journalCount = enqueueJournal()
        val dailySignalCount = enqueueDailySignals()
        val healthRecordCount = enqueueHealthRecords()

        return CloudSyncBackfillReport(
            meals = mealCount,
            waterEntries = waterCount,
            todos = todoCount,
            goals = goalCount,
            journalEntries = journalCount,
            dailySignals = dailySignalCount,
            healthRecords = healthRecordCount
        )
    }

    private suspend fun enqueueMeals(): Int {
        val dao = database.mealDao()
        val candidates = dao.getUnsyncedSyncIds().map { id ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.MEAL,
                entityId = id,
                shouldWrite = { dao.getBySyncId(id)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getBySyncId(id)) {
                        "Meal $id disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Meal $id was already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.updateMeal(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueWater(): Int {
        val dao = database.waterDao()
        val candidates = dao.getUnsyncedSyncIds().map { id ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.WATER_ENTRY,
                entityId = id,
                shouldWrite = { dao.getBySyncId(id)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getBySyncId(id)) {
                        "Water entry $id disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Water entry $id was already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.updateWater(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueTodos(): Int {
        val dao = database.todoDao()
        val candidates = dao.getUnsyncedSyncIds().map { id ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.TODO,
                entityId = id,
                shouldWrite = { dao.getBySyncId(id)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getBySyncId(id)) {
                        "Todo $id disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Todo $id was already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.update(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueGoals(): Int {
        val dao = database.goalDao()
        val candidates = dao.getUnsyncedSyncIds().map { id ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.GOAL,
                entityId = id,
                shouldWrite = { dao.getBySyncId(id)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getBySyncId(id)) {
                        "Goal $id disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Goal $id was already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.update(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueJournal(): Int {
        val dao = database.journalDao()
        val candidates = dao.getUnsyncedSyncIds().map { id ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.JOURNAL_ENTRY,
                entityId = id,
                shouldWrite = { dao.getBySyncId(id)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getBySyncId(id)) {
                        "Journal entry $id disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Journal entry $id was already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.update(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueDailySignals(): Int {
        val dao = database.dailySignalsDao()
        val candidates = dao.getUnsyncedDates().map { date ->
            val entityId = "${writer.deviceId}:$date"
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.DAILY_SIGNALS,
                entityId = entityId,
                shouldWrite = { dao.getSignalForDate(date)?.syncHlc == null },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getSignalForDate(date)) {
                        "Daily signals for $date disappeared during cloud backfill"
                    }
                    check(current.syncHlc == null) { "Daily signals for $date were already cloud-stamped" }
                    val updated = current.copy(syncHlc = hlc)
                    dao.insertOrUpdate(updated)
                    updated.toCloudSyncPayload(writer.deviceId)
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun enqueueHealthRecords(): Int {
        val dao = database.healthConnectArchiveDao()
        val candidates = dao.getUnsyncedArchiveIds().map { archiveId ->
            CloudSyncBatchMutation(
                entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                entityId = archiveId,
                shouldWrite = {
                    val current = dao.getByArchiveId(archiveId)
                    current != null && !current.isDeleted && current.syncHlc == null
                },
                mutation = { hlc ->
                    val current = requireNotNull(dao.getByArchiveId(archiveId)) {
                        "Health record $archiveId disappeared during cloud backfill"
                    }
                    check(!current.isDeleted && current.syncHlc == null) {
                        "Health record $archiveId is deleted or already cloud-stamped"
                    }
                    val updated = current.copy(syncHlc = hlc)
                    dao.upsertRecord(updated)
                    updated.toCloudSyncPayload()
                }
            )
        }
        return commit(candidates)
    }

    private suspend fun commit(
        candidates: List<CloudSyncBatchMutation>
    ): Int {
        if (candidates.isEmpty()) return 0
        var written = 0
        val guarded = candidates.map { item ->
            item.copy(
                shouldWrite = {
                    val shouldWrite = item.shouldWrite()
                    if (shouldWrite) written++
                    shouldWrite
                }
            )
        }
        guarded.chunked(BATCH_SIZE).forEach { chunk ->
            writer.upsertBatch(chunk)
        }
        return written
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}

data class CloudSyncBackfillReport(
    val meals: Int,
    val waterEntries: Int,
    val todos: Int,
    val goals: Int,
    val journalEntries: Int,
    val dailySignals: Int,
    val healthRecords: Int
) {
    val totalRecords: Int
        get() = meals + waterEntries + todos + goals + journalEntries + dailySignals + healthRecords
}
