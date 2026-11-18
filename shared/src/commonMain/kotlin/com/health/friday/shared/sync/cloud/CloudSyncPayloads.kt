package com.health.friday.shared.sync.cloud

import kotlinx.serialization.Serializable

object CloudSyncEntityTypes {
    const val MEAL = "meal"
    const val WATER_ENTRY = "water_entry"
    const val TODO = "todo"
    const val GOAL = "goal"
    const val JOURNAL_ENTRY = "journal_entry"
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
