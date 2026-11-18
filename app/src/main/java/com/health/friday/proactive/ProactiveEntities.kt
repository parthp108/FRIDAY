package com.health.friday.proactive

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nudge_history")
data class NudgeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timestamp: Long,
    val interacted: Boolean
)

@Entity(tableName = "protocols")
data class ProtocolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerConditionJson: String,
    val actionTool: String,
    val actionPayloadJson: String,
    val autonomyLevel: String, // "ASK_FIRST", "TELL_AFTER", "SILENT_AUTO"
    val isEnabled: Boolean = true
)