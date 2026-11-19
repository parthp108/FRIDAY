package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.repository.HealthRepository
import com.health.friday.desktop.repository.NutritionRepository
import com.health.friday.desktop.repository.TasksRepository
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayMealDto
import com.health.friday.shared.models.FridayTaskDto
import com.health.friday.shared.models.HydrationLogDto
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.DailySignalsSyncPayload
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import com.health.friday.shared.sync.cloud.HealthRecordSyncPayload
import com.health.friday.shared.sync.cloud.JournalSyncPayload
import com.health.friday.shared.sync.cloud.MealSyncPayload
import com.health.friday.shared.sync.cloud.SyncOperation
import com.health.friday.shared.sync.cloud.SyncOperationType
import com.health.friday.shared.sync.cloud.TodoSyncPayload
import com.health.friday.shared.sync.cloud.WaterSyncPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * Validates remote payload identity and projects the committed snapshot into
 * desktop repository StateFlows. The durable snapshot itself is owned by
 * DesktopCloudSyncStore, not by these in-memory projections.
 */
class DesktopCloudSyncRemoteApplier(
    private val nutritionRepository: NutritionRepository,
    private val healthRepository: HealthRepository,
    private val tasksRepository: TasksRepository
) {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun publishSnapshot(records: List<DesktopCloudSyncEntityRecord>) {
        val active = records.filterNot { it.isDeleted }

        val meals = active.filter { it.entityType == CloudSyncEntityTypes.MEAL }
            .map { row ->
                val payload = decode<MealSyncPayload>(row.payloadJson, "meal")
                require(payload.syncId == row.entityId) { "Meal payload ID does not match its cloud entity ID." }
                FridayMealDto(
                    id = payload.syncId,
                    name = payload.name,
                    calories = payload.calories,
                    protein = payload.protein.toFloat(),
                    carbs = payload.carbohydrates.toFloat(),
                    fat = payload.fat.toFloat(),
                    timestamp = payload.timestamp
                )
            }
        val deletedMealIds = records.asSequence()
            .filter { it.entityType == CloudSyncEntityTypes.MEAL && it.isDeleted }
            .map { it.entityId }
            .toSet()
        nutritionRepository.setCloudMeals(meals, deletedMealIds)

        val water = active.filter { it.entityType == CloudSyncEntityTypes.WATER_ENTRY }
            .map { row ->
                val payload = decode<WaterSyncPayload>(row.payloadJson, "water")
                require(payload.syncId == row.entityId) { "Water payload ID does not match its cloud entity ID." }
                HydrationLogDto(payload.syncId, payload.amountMl, payload.timestamp)
            }
        healthRepository.setCloudWaterEntries(water)

        val todos = active.filter { it.entityType == CloudSyncEntityTypes.TODO }
            .map { row ->
                val payload = decode<TodoSyncPayload>(row.payloadJson, "todo")
                require(payload.syncId == row.entityId) { "Todo payload ID does not match its cloud entity ID." }
                FridayTaskDto(
                    id = payload.syncId,
                    title = payload.title,
                    isCompleted = payload.isDone,
                    deadlineTimestamp = payload.dueAt,
                    createdTimestamp = payload.createdAt,
                    completedTimestamp = payload.completedAt,
                    timestamp = payload.createdAt
                )
            }
        val deletedTodoIds = records.asSequence()
            .filter { it.entityType == CloudSyncEntityTypes.TODO && it.isDeleted }
            .map { it.entityId }
            .toSet()
        tasksRepository.setCloudTasks(todos, deletedTodoIds)

        val goals = active.filter { it.entityType == CloudSyncEntityTypes.GOAL }
            .map { row ->
                val payload = decode<GoalSyncPayload>(row.payloadJson, "goal")
                require(payload.syncId == row.entityId) { "Goal payload ID does not match its cloud entity ID." }
                payload
            }
        tasksRepository.setCloudGoals(goals)

        val journalEntries = active.filter { it.entityType == CloudSyncEntityTypes.JOURNAL_ENTRY }
            .map { row ->
                val payload = decode<JournalSyncPayload>(row.payloadJson, "journal entry")
                require(payload.syncId == row.entityId) { "Journal payload ID does not match its cloud entity ID." }
                payload
            }
        tasksRepository.setCloudJournalEntries(journalEntries)

        val latestSignals = active.asSequence()
            .filter { it.entityType == CloudSyncEntityTypes.DAILY_SIGNALS }
            .map { row ->
                val payload = decode<DailySignalsSyncPayload>(row.payloadJson, "daily signals")
                require(row.entityId == "${payload.sourceDeviceId}:${payload.date}") {
                    "Daily-signals payload identity does not match its cloud entity ID."
                }
                require(runCatching { LocalDate.parse(payload.date) }.isSuccess) {
                    "Daily-signals payload contains an invalid date."
                }
                row to payload
            }
            .maxWithOrNull(
                compareBy<Pair<DesktopCloudSyncEntityRecord, DailySignalsSyncPayload>> { it.second.date }
                    .thenBy { it.first.hlc }
            )
            ?.second

        healthRepository.setCloudSignals(latestSignals?.let { payload ->
            DailySignalDto(
                date = payload.date,
                restingHeartRate = payload.restingHr ?: 0,
                hrv = payload.hrvMs?.toFloat() ?: 0f,
                stepCount = payload.steps ?: 0,
                activeEnergyBurned = payload.activeCaloriesKcal ?: 0,
                sleepDurationMinutes = payload.sleepMinutes ?: 0,
                waterIntakeMl = payload.waterMl,
                phoneUsageMinutes = payload.screenTimeMinutes,
                bloodOxygenPercentage = payload.spo2Percent?.toFloat() ?: 0f,
                timestamp = records.asSequence()
                    .filter { it.entityType == CloudSyncEntityTypes.DAILY_SIGNALS && it.entityId == "${payload.sourceDeviceId}:${payload.date}" }
                    .mapNotNull { it.hlc.substringBefore('-').toLongOrNull() }
                    .firstOrNull() ?: 0L
            )
        })
    }

    private inline fun <reified T> decode(raw: String?, label: String): T {
        require(!raw.isNullOrBlank()) { "$label UPSERT is missing its stored payload." }
        return json.decodeFromString(raw)
    }

    companion object {
        fun recordFrom(operation: SyncOperation): DesktopCloudSyncEntityRecord {
            operation.validate()
            require(operation.schemaVersion == 1) {
                "Unsupported cloud operation schema version: ${operation.schemaVersion}."
            }
            require(operation.entityType in setOf(
                CloudSyncEntityTypes.MEAL,
                CloudSyncEntityTypes.WATER_ENTRY,
                CloudSyncEntityTypes.TODO,
                CloudSyncEntityTypes.GOAL,
                CloudSyncEntityTypes.JOURNAL_ENTRY,
                CloudSyncEntityTypes.DAILY_SIGNALS,
                CloudSyncEntityTypes.HEALTH_RECORD
            )) { "Unsupported cloud sync entity type: ${operation.entityType}" }
            val payloadJson = when (operation.operationType) {
                SyncOperationType.DELETE -> {
                    require(operation.payload == null) { "DELETE operations must not contain a payload." }
                    validateDeleteIdentity(operation.entityType, operation.entityId)
                    null
                }
                SyncOperationType.UPSERT -> {
                    val payload = requireNotNull(operation.payload) { "UPSERT operation is missing its payload." }
                    validatePayloadIdentity(operation.entityType, operation.entityId, payload)
                    Json.encodeToString(JsonObject.serializer(), payload)
                }
            }
            return DesktopCloudSyncEntityRecord(
                entityType = operation.entityType,
                entityId = operation.entityId,
                hlc = operation.hlc,
                isDeleted = operation.operationType == SyncOperationType.DELETE,
                payloadJson = payloadJson
            )
        }

        private fun validateDeleteIdentity(type: String, entityId: String) {
            when (type) {
                CloudSyncEntityTypes.DAILY_SIGNALS -> {
                    val separator = entityId.lastIndexOf(':')
                    require(separator > 0 && separator < entityId.lastIndex) {
                        "Daily-signals DELETE entityId must be '<sourceDeviceId>:<YYYY-MM-DD>'."
                    }
                    require(runCatching { LocalDate.parse(entityId.substring(separator + 1)) }.isSuccess) {
                        "Daily-signals DELETE entityId contains an invalid date."
                    }
                }
                CloudSyncEntityTypes.HEALTH_RECORD -> {
                    val first = entityId.indexOf(':')
                    val second = if (first >= 0) entityId.indexOf(':', first + 1) else -1
                    require(first > 0 && second > first + 1 && second < entityId.lastIndex) {
                        "Health-record DELETE entityId must be '<sourceDeviceId>:<recordType>:<recordId>'."
                    }
                }
            }
        }

        private fun validatePayloadIdentity(type: String, entityId: String, payload: JsonObject) {
            val json = Json { ignoreUnknownKeys = true }
            when (type) {
                CloudSyncEntityTypes.MEAL -> require(
                    json.decodeFromJsonElement(MealSyncPayload.serializer(), payload).syncId == entityId
                ) { "Meal payload syncId does not match operation entityId." }
                CloudSyncEntityTypes.WATER_ENTRY -> require(
                    json.decodeFromJsonElement(WaterSyncPayload.serializer(), payload).syncId == entityId
                ) { "Water payload syncId does not match operation entityId." }
                CloudSyncEntityTypes.TODO -> require(
                    json.decodeFromJsonElement(TodoSyncPayload.serializer(), payload).syncId == entityId
                ) { "Todo payload syncId does not match operation entityId." }
                CloudSyncEntityTypes.GOAL -> require(
                    json.decodeFromJsonElement(GoalSyncPayload.serializer(), payload).syncId == entityId
                ) { "Goal payload syncId does not match operation entityId." }
                CloudSyncEntityTypes.JOURNAL_ENTRY -> require(
                    json.decodeFromJsonElement(JournalSyncPayload.serializer(), payload).syncId == entityId
                ) { "Journal payload syncId does not match operation entityId." }
                CloudSyncEntityTypes.DAILY_SIGNALS -> {
                    val value = json.decodeFromJsonElement(DailySignalsSyncPayload.serializer(), payload)
                    require(value.date.isNotBlank() && value.sourceDeviceId.isNotBlank()) {
                        "Daily-signals payload requires a date and source device ID."
                    }
                    require(runCatching { LocalDate.parse(value.date) }.isSuccess) {
                        "Daily-signals payload has an invalid date."
                    }
                    require(entityId == "${value.sourceDeviceId}:${value.date}") {
                        "Daily-signals payload identity does not match operation entityId."
                    }
                }
                CloudSyncEntityTypes.HEALTH_RECORD -> {
                    val value = json.decodeFromJsonElement(HealthRecordSyncPayload.serializer(), payload)
                    require(value.archiveId == entityId) {
                        "Health-record archiveId does not match operation entityId."
                    }
                    require(value.sourceDeviceId.isNotBlank() && value.recordType.isNotBlank() && value.recordId.isNotBlank()) {
                        "Health-record identity fields must not be blank."
                    }
                    require(value.archiveId == "${value.sourceDeviceId}:${value.recordType}:${value.recordId}") {
                        "Health-record archiveId does not match its source/type/record identity."
                    }
                }
                else -> throw IllegalArgumentException("Unsupported cloud sync entity type: $type")
            }
        }
    }
}
