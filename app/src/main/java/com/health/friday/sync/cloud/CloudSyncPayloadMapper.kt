package com.health.friday.sync.cloud

import com.health.friday.data.local.*
import com.health.friday.shared.sync.cloud.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private val syncPayloadJson = Json {
    encodeDefaults = true
}

fun Meal.toCloudSyncPayload(): JsonObject =
    syncPayloadJson.encodeToJsonElement(
        MealSyncPayload.serializer(),
        MealSyncPayload(
            syncId = syncId,
            name = name,
            mealType = mealType,
            calories = calories,
            protein = protein,
            carbohydrates = carbohydrates,
            fat = fat,
            timestamp = timestamp,
            isEstimated = isEstimated,
            nutrientsJson = nutrientsJson
        )
    ).jsonObject

fun WaterEntry.toCloudSyncPayload(): JsonObject =
    syncPayloadJson.encodeToJsonElement(
        WaterSyncPayload.serializer(),
        WaterSyncPayload(
            syncId = syncId,
            amountMl = amountMl,
            timestamp = timestamp
        )
    ).jsonObject

fun TodoItem.toCloudSyncPayload(): JsonObject =
    syncPayloadJson.encodeToJsonElement(
        TodoSyncPayload.serializer(),
        TodoSyncPayload(
            syncId = syncId,
            title = title,
            isDone = isDone,
            createdAt = createdAt,
            completedAt = completedAt,
            dueAt = dueAt
        )
    ).jsonObject

fun Goal.toCloudSyncPayload(): JsonObject =
    syncPayloadJson.encodeToJsonElement(
        GoalSyncPayload.serializer(),
        GoalSyncPayload(
            syncId = syncId,
            title = title,
            targetDate = targetDate,
            isDone = isDone,
            createdAt = createdAt
        )
    ).jsonObject

fun JournalEntry.toCloudSyncPayload(): JsonObject =
    syncPayloadJson.encodeToJsonElement(
        JournalSyncPayload.serializer(),
        JournalSyncPayload(
            syncId = syncId,
            text = text,
            feedback = feedback,
            createdAt = createdAt
        )
    ).jsonObject
