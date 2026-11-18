package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val description: String,     // e.g., "Logged 450 kcal of Biryani"
    val inverseTool: String,     // e.g., "delete_meal"
    val inversePayloadJson: String // e.g., "{\"mealId\": 42}"
)