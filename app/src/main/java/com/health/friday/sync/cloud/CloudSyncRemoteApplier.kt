package com.health.friday.sync.cloud

import androidx.room.withTransaction
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.data.local.FridayDatabase
import com.health.friday.data.local.Goal
import com.health.friday.data.local.HealthRecordArchiveEntity
import com.health.friday.data.local.JournalEntry
import com.health.friday.data.local.Meal
import com.health.friday.data.local.TodoItem
import com.health.friday.data.local.WaterEntry
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
            for (op in batch.operations) {
                if (applyOperation(op)) {
                    appliedCount++
                }
            }
        }

        return appliedCount
    }

    private suspend fun applyOperation(op: SyncOperation): Boolean {
        op.validate()

        com.health.friday.shared.protocol.HlcTimestamp.parse(op.hlc)?.let {
            clock.receive(it)
        }

        return when (op.entityType) {
            CloudSyncEntityTypes.MEAL -> applyMeal(op)
            CloudSyncEntityTypes.WATER_ENTRY -> applyWater(op)
            CloudSyncEntityTypes.TODO -> applyTodo(op)
            CloudSyncEntityTypes.GOAL -> applyGoal(op)
            CloudSyncEntityTypes.JOURNAL_ENTRY -> applyJournal(op)
            CloudSyncEntityTypes.DAILY_SIGNALS -> applyDailySignals(op)
            CloudSyncEntityTypes.HEALTH_RECORD -> applyHealthRecord(op)
            else -> false
        }
    }

    private suspend fun applyMeal(op: SyncOperation): Boolean {
        val existing = database.mealDao().getBySyncId(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            database.mealDao().deleteBySyncId(op.entityId)
            return true
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(MealSyncPayload.serializer(), payloadObj)

        val meal = Meal(
            id = existing?.id ?: 0,
            syncId = payload.syncId,
            syncHlc = op.hlc,
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

        database.mealDao().insertMeal(meal)
        return true
    }

    private suspend fun applyWater(op: SyncOperation): Boolean {
        val existing = database.waterDao().getBySyncId(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            database.waterDao().deleteBySyncId(op.entityId)
            return true
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(WaterSyncPayload.serializer(), payloadObj)

        val water = WaterEntry(
            id = existing?.id ?: 0,
            syncId = payload.syncId,
            syncHlc = op.hlc,
            amountMl = payload.amountMl,
            timestamp = payload.timestamp
        )

        database.waterDao().insertWater(water)
        return true
    }

    private suspend fun applyTodo(op: SyncOperation): Boolean {
        val existing = database.todoDao().getBySyncId(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            database.todoDao().deleteBySyncId(op.entityId)
            return true
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(TodoSyncPayload.serializer(), payloadObj)

        val todo = TodoItem(
            id = existing?.id ?: 0,
            syncId = payload.syncId,
            syncHlc = op.hlc,
            title = payload.title,
            isDone = payload.isDone,
            createdAt = payload.createdAt,
            completedAt = payload.completedAt,
            dueAt = payload.dueAt
        )

        database.todoDao().insert(todo)
        return true
    }

    private suspend fun applyGoal(op: SyncOperation): Boolean {
        val existing = database.goalDao().getBySyncId(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            database.goalDao().deleteBySyncId(op.entityId)
            return true
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(GoalSyncPayload.serializer(), payloadObj)

        val goal = Goal(
            id = existing?.id ?: 0,
            syncId = payload.syncId,
            syncHlc = op.hlc,
            title = payload.title,
            targetDate = payload.targetDate,
            isDone = payload.isDone,
            createdAt = payload.createdAt
        )

        database.goalDao().insert(goal)
        return true
    }

    private suspend fun applyJournal(op: SyncOperation): Boolean {
        val existing = database.journalDao().getBySyncId(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            database.journalDao().deleteBySyncId(op.entityId)
            return true
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(JournalSyncPayload.serializer(), payloadObj)

        val journal = JournalEntry(
            id = existing?.id ?: 0,
            syncId = payload.syncId,
            syncHlc = op.hlc,
            text = payload.text,
            feedback = payload.feedback,
            createdAt = payload.createdAt
        )

        database.journalDao().insert(journal)
        return true
    }

    private suspend fun applyDailySignals(op: SyncOperation): Boolean {
        val existing = database.dailySignalsDao().getSignalForDate(op.entityId)
        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            return false
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(DailySignalsSyncPayload.serializer(), payloadObj)

        val signal = DailySignalsEntity(
            date = payload.date,
            syncHlc = op.hlc,
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

        database.dailySignalsDao().insertOrUpdate(signal)
        return true
    }

    private suspend fun applyHealthRecord(op: SyncOperation): Boolean {
        val archiveDao = database.healthConnectArchiveDao()
        val existing = archiveDao.getRecords(recordType = op.entityType, limit = 1000)
            .firstOrNull { it.archiveId == op.entityId }

        if (existing != null && existing.syncHlc != null && existing.syncHlc >= op.hlc) {
            return false
        }

        if (op.operationType == SyncOperationType.DELETE) {
            val parts = op.entityId.split(":")
            if (parts.size >= 3) {
                archiveDao.markDeleted(parts[0], parts[1], parts[2], op.hlc)
                return true
            }
            return false
        }

        val payloadObj = op.payload ?: return false
        val payload = json.decodeFromJsonElement(HealthRecordSyncPayload.serializer(), payloadObj)

        val archiveEntity = HealthRecordArchiveEntity(
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
            syncHlc = op.hlc,
            isDeleted = false
        )

        archiveDao.upsertRecord(archiveEntity)
        return true
    }
}
