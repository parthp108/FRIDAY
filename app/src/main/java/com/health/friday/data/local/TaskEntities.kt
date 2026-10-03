package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val isDone: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    val completedAt: Long? = null,

    val dueAt: Long? = null
)

@Entity(tableName = "goals")
data class Goal(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val targetDate: Long? = null,

    val isDone: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "journal_entries")
data class JournalEntry(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val text: String,

    val feedback: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)