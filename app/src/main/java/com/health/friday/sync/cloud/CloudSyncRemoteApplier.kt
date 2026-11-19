package com.health.friday.sync.cloud

import androidx.room.withTransaction
import com.health.friday.data.local.CloudSyncEntityStateEntity
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.data.local.FridayDatabase
import com.health.friday.data.local.Goal
import com.health.friday.data.local.HealthRecordArchiveEntity
import com.health.friday.data.local.JournalEntry
import com.health.friday.data.local.Meal
import com.health.friday.data.local.TodoItem
import com.health.friday.data.local.WaterEntry
import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.DailySignalsSyncPayload
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import com.health.friday.shared.sync.cloud.HealthRecordSyncPayload
import com.health.friday.shared.sync.cloud.JournalSyncPayload
import com.health.friday.shared.sync.cloud.MealSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncOperation
import com.health.friday.shared.sync.cloud.SyncOperationType
import com.health.friday.shared.sync.cloud.TodoSyncPayload
import com.health.friday.shared.sync.cloud.WaterSyncPayload
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Applies remote cloud operations to FRIDAY's Room database.
 *
 * The caller (AndroidCloudSyncStore) inserts inbox IDs, invokes this applier,
 * and advances the Drive cursor in one outer Room transaction. Any malformed
 * or unsupported operation throws, rolling back the whole batch transaction.
 */
class RoomCloudSyncRemoteApplier(
    private val database: FridayDatabase,
    private val clock: AndroidHybridLogicalClock
) : CloudSyncRemoteApplier {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun apply(operation: SyncOperation) {
        applyOperation(operation)
    }

    suspend fun applyBatch(batch: SyncBatch): Int {
        batch.validate()
        var appliedCount = 0

        database.withTransaction {
            for (operation in batch.operations) {
                if (applyOperation(operation)) appliedCount++
            }
        }

        return appliedCount
    }

    /** Returns false only when the incoming operation is older/equal to known state. */
    private suspend fun applyOperation(operation: SyncOperation): Boolean {
        operation.validate()
        require(operation.schemaVersion == 1) {
            "Unsupported sync operation schema version: ${operation.schemaVersion}"
        }

        val incomingHlc = HlcTimestamp.parse(operation.hlc)
            ?: throw IllegalArgumentException("Malformed operation HLC")

        // Merge even an operation that later proves stale. A future local
        // mutation must still receive an HLC greater than any remote timestamp seen.
        clock.receive(incomingHlc)

        return when (operation.entityType) {
            CloudSyncEntityTypes.MEAL -> applyMeal(operation)
            CloudSyncEntityTypes.WATER_ENTRY -> applyWater(operation)
            CloudSyncEntityTypes.TODO -> applyTodo(operation)
            CloudSyncEntityTypes.GOAL -> applyGoal(operation)
            CloudSyncEntityTypes.JOURNAL_ENTRY -> applyJournal(operation)
            CloudSyncEntityTypes.DAILY_SIGNALS -> applyDailySignals(operation)
            CloudSyncEntityTypes.HEALTH_RECORD -> applyHealthRecord(operation)
            else -> throw IllegalArgumentException(
                "Unsupported cloud sync entity type: ${operation.entityType}"
            )
        }
    }

    private suspend fun isStale(
        operation: SyncOperation,
        legacyRowHlc: String?
    ): Boolean {
        val state = database.cloudSyncDao().getEntityState(
            entityType = operation.entityType,
            entityId = operation.entityId
        )

        return !CloudSyncVersioning.shouldApply(
            incomingHlc = operation.hlc,
            persistedHlcs = listOf(state?.lastHlc, legacyRowHlc)
        )
    }

    private suspend fun saveVersion(
        operation: SyncOperation,
        deleted: Boolean
    ) {
        database.cloudSyncDao().saveEntityState(
            CloudSyncEntityStateEntity(
                entityType = operation.entityType,
                entityId = operation.entityId,
                lastHlc = operation.hlc,
                isDeleted = deleted,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun requireUpsert(operation: SyncOperation) {
        require(operation.operationType == SyncOperationType.UPSERT) {
            "Expected UPSERT for ${operation.entityType}/${operation.entityId}"
        }
    }

    private suspend fun applyMeal(operation: SyncOperation): Boolean {
        val dao = database.mealDao()
        val existing = dao.getBySyncId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteBySyncId(operation.entityId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            MealSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Meal UPSERT is missing payload" }
        )
        require(payload.syncId == operation.entityId) {
            "Meal payload syncId does not match operation entityId"
        }

        val entity = Meal(
            id = existing?.id ?: 0L,
            syncId = payload.syncId,
            syncHlc = operation.hlc,
            name = payload.name,
            mealType = payload.mealType,
            calories = payload.calories,
            protein = payload.protein,
            carbohydrates = payload.carbohydrates,
            fat = payload.fat,
            timestamp = payload.timestamp,
            isEstimated = payload.isEstimated,
            nutrientsJson = payload.nutrientsJson
        )

        if (existing == null) dao.insertMeal(entity) else dao.updateMeal(entity)
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyWater(operation: SyncOperation): Boolean {
        val dao = database.waterDao()
        val existing = dao.getBySyncId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteBySyncId(operation.entityId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            WaterSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Water UPSERT is missing payload" }
        )
        require(payload.syncId == operation.entityId) {
            "Water payload syncId does not match operation entityId"
        }

        val entity = WaterEntry(
            id = existing?.id ?: 0L,
            syncId = payload.syncId,
            syncHlc = operation.hlc,
            amountMl = payload.amountMl,
            timestamp = payload.timestamp
        )

        if (existing == null) dao.insertWater(entity) else dao.updateWater(entity)
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyTodo(operation: SyncOperation): Boolean {
        val dao = database.todoDao()
        val existing = dao.getBySyncId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteBySyncId(operation.entityId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            TodoSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Todo UPSERT is missing payload" }
        )
        require(payload.syncId == operation.entityId) {
            "Todo payload syncId does not match operation entityId"
        }

        val entity = TodoItem(
            id = existing?.id ?: 0L,
            syncId = payload.syncId,
            syncHlc = operation.hlc,
            title = payload.title,
            isDone = payload.isDone,
            createdAt = payload.createdAt,
            completedAt = payload.completedAt,
            dueAt = payload.dueAt,
            embeddingJson = existing?.embeddingJson
        )

        if (existing == null) dao.insert(entity) else dao.update(entity)
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyGoal(operation: SyncOperation): Boolean {
        val dao = database.goalDao()
        val existing = dao.getBySyncId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteBySyncId(operation.entityId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            GoalSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Goal UPSERT is missing payload" }
        )
        require(payload.syncId == operation.entityId) {
            "Goal payload syncId does not match operation entityId"
        }

        val entity = Goal(
            id = existing?.id ?: 0L,
            syncId = payload.syncId,
            syncHlc = operation.hlc,
            title = payload.title,
            targetDate = payload.targetDate,
            isDone = payload.isDone,
            createdAt = payload.createdAt,
            embeddingJson = existing?.embeddingJson
        )

        if (existing == null) dao.insert(entity) else dao.update(entity)
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyJournal(operation: SyncOperation): Boolean {
        val dao = database.journalDao()
        val existing = dao.getBySyncId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteBySyncId(operation.entityId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            JournalSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Journal UPSERT is missing payload" }
        )
        require(payload.syncId == operation.entityId) {
            "Journal payload syncId does not match operation entityId"
        }

        val entity = JournalEntry(
            id = existing?.id ?: 0L,
            syncId = payload.syncId,
            syncHlc = operation.hlc,
            text = payload.text,
            feedback = payload.feedback,
            createdAt = payload.createdAt,
            embeddingJson = existing?.embeddingJson
        )

        if (existing == null) dao.insert(entity) else dao.update(entity)
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyDailySignals(operation: SyncOperation): Boolean {
        val dao = database.dailySignalsDao()
        val entityIdSeparator = operation.entityId.lastIndexOf(':')
        require(entityIdSeparator > 0 && entityIdSeparator < operation.entityId.lastIndex) {
            "Daily signals entityId must be '<sourceDeviceId>:<YYYY-MM-DD>'"
        }
        val sourceDeviceIdFromId = operation.entityId.substring(0, entityIdSeparator)
        val dateFromId = operation.entityId.substring(entityIdSeparator + 1)
        require(sourceDeviceIdFromId.isNotBlank()) {
            "Daily signals entityId source device must not be blank"
        }
        require(runCatching { LocalDate.parse(dateFromId) }.isSuccess) {
            "Daily signals entityId contains an invalid date"
        }

        // Daily signals are stored locally by date, while their cloud identity
        // includes the originating device: '<deviceId>:<date>'.
        val existing = dao.getSignalForDate(dateFromId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            dao.deleteByDate(dateFromId)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            DailySignalsSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Daily signals UPSERT is missing payload" }
        )
        require(payload.sourceDeviceId == sourceDeviceIdFromId &&
            payload.date == dateFromId &&
            operation.entityId == "${payload.sourceDeviceId}:${payload.date}") {
            "Daily signals payload identity does not match operation entityId"
        }

        dao.insertOrUpdate(
            DailySignalsEntity(
                date = payload.date,
                syncHlc = operation.hlc,
                sleepMinutes = payload.sleepMinutes,
                sleepStartEpoch = payload.sleepStartEpoch,
                sleepEndEpoch = payload.sleepEndEpoch,
                steps = payload.steps,
                restingHr = payload.restingHr,
                kcal = payload.kcal,
                proteinGrams = payload.proteinGrams,
                carbsGrams = payload.carbsGrams,
                fatGrams = payload.fatGrams,
                waterMl = payload.waterMl,
                lastMealHour = payload.lastMealHour,
                screenTimeMinutes = payload.screenTimeMinutes,
                tasksCompleted = payload.tasksCompleted,
                tasksPending = payload.tasksPending,
                moodScore = payload.moodScore,
                workoutMinutes = payload.workoutMinutes,
                activeCaloriesKcal = payload.activeCaloriesKcal,
                distanceMeters = payload.distanceMeters,
                hrvMs = payload.hrvMs,
                spo2Percent = payload.spo2Percent
            )
        )
        saveVersion(operation, deleted = false)
        return true
    }

    private suspend fun applyHealthRecord(operation: SyncOperation): Boolean {
        val dao = database.healthConnectArchiveDao()
        val existing = dao.getByArchiveId(operation.entityId)
        if (isStale(operation, existing?.syncHlc)) return false

        if (operation.operationType == SyncOperationType.DELETE) {
            // The entity-state table stores the tombstone even when the archive
            // row has never arrived on this device.
            dao.markDeletedByArchiveId(operation.entityId, operation.hlc)
            saveVersion(operation, deleted = true)
            return true
        }

        requireUpsert(operation)
        val payload = json.decodeFromJsonElement(
            HealthRecordSyncPayload.serializer(),
            requireNotNull(operation.payload) { "Health record UPSERT is missing payload" }
        )
        require(payload.archiveId == operation.entityId) {
            "Health record archiveId does not match operation entityId"
        }
        require(payload.sourceDeviceId.isNotBlank()) {
            "Health record sourceDeviceId must not be blank"
        }
        require(payload.recordType.isNotBlank() && payload.recordId.isNotBlank()) {
            "Health record identity fields must not be blank"
        }
        require(
            payload.archiveId == "${payload.sourceDeviceId}:${payload.recordType}:${payload.recordId}"
        ) {
            "Health record archiveId does not match its source/type/record identity"
        }

        dao.upsertRecord(
            HealthRecordArchiveEntity(
                archiveId = payload.archiveId,
                sourceDeviceId = payload.sourceDeviceId,
                recordType = payload.recordType,
                recordId = payload.recordId,
                dataOriginPackage = payload.dataOriginPackage,
                clientRecordId = payload.clientRecordId,
                clientRecordVersion = payload.clientRecordVersion,
                lastModifiedTimeMs = payload.lastModifiedTimeMs,
                recordingMethod = payload.recordingMethod,
                deviceManufacturer = payload.deviceManufacturer,
                deviceModel = payload.deviceModel,
                deviceType = payload.deviceType,
                startTimeMs = payload.startTimeMs,
                endTimeMs = payload.endTimeMs,
                payloadJson = payload.payloadJson,
                syncHlc = operation.hlc,
                isDeleted = false
            )
        )
        saveVersion(operation, deleted = false)
        return true
    }
}
