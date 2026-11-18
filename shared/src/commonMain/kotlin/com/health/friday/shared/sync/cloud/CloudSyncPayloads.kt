package com.health.friday.shared.sync.cloud

import kotlinx.serialization.Serializable

object CloudSyncEntityTypes {
    const val MEAL = "meal"
    const val WATER_ENTRY = "water_entry"
    const val TODO = "todo"
    const val GOAL = "goal"
    const val JOURNAL_ENTRY = "journal_entry"
    const val DAILY_SIGNALS = "daily_signals"
    const val HEALTH_RECORD = "health_record"
}

@Serializable
data class MealSyncPayload(
    val syncId: String,
    val name: String,
    val mealType: String,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val timestamp: Long,
    val isEstimated: Boolean,
    val nutrientsJson: String
)

@Serializable
data class WaterSyncPayload(
    val syncId: String,
    val amountMl: Int,
    val timestamp: Long
)

@Serializable
data class TodoSyncPayload(
    val syncId: String,
    val title: String,
    val isDone: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
    val dueAt: Long?
)

@Serializable
data class GoalSyncPayload(
    val syncId: String,
    val title: String,
    val targetDate: Long?,
    val isDone: Boolean,
    val createdAt: Long
)

@Serializable
data class JournalSyncPayload(
    val syncId: String,
    val text: String,
    val feedback: String?,
    val createdAt: Long
)

@Serializable
data class DailySignalsSyncPayload(
    val date: String,
    val sourceDeviceId: String,

    val sleepMinutes: Int?,
    val sleepStartEpoch: Long?,
    val sleepEndEpoch: Long?,
    val steps: Int?,
    val restingHr: Int?,

    val kcal: Int?,
    val proteinGrams: Float?,
    val carbsGrams: Float?,
    val fatGrams: Float?,
    val waterMl: Int?,
    val lastMealHour: Float?,

    val screenTimeMinutes: Int?,
    val tasksCompleted: Int,
    val tasksPending: Int,
    val moodScore: Int?,
    val workoutMinutes: Int,

    val activeCaloriesKcal: Int?,
    val distanceMeters: Double?,
    val hrvMs: Double?,
    val spo2Percent: Double?
)

@Serializable
data class HealthRecordSyncPayload(
    val archiveId: String,
    val sourceDeviceId: String,

    val recordType: String,
    val recordId: String,

    val dataOriginPackage: String,

    val clientRecordId: String?,
    val clientRecordVersion: Long,

    val lastModifiedTimeMs: Long,
    val recordingMethod: Int,

    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: Int?,

    val startTimeMs: Long?,
    val endTimeMs: Long?,

    val payloadJson: String
)
