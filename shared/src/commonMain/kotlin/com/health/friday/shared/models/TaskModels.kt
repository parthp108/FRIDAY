package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class FridayTaskDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val category: String = "general",
    val deadlineTimestamp: Long? = null,
    val createdTimestamp: Long = 0L,
    val completedTimestamp: Long? = null,
    val timestamp: Long = 0L
)

@Serializable
data class FridayReminderDto(
    val id: String,
    val message: String,
    val timeMillis: Long,
    val isTriggered: Boolean = false,
    val isRecurring: Boolean = false
)
