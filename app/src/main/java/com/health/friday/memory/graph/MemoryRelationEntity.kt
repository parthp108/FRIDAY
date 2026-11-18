package com.health.friday.memory.graph

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_relations")
data class MemoryRelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectFactId: Long,
    val predicate: String,
    val objectFactId: Long,
    val confidenceScore: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)
