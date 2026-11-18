package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.health.friday.shared.protocol.Ids

@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val embeddingJson: String? = null,
    val completedAt: Long? = null,
    val dueAt: Long? = null,

    @ColumnInfo(defaultValue = "''")
    val syncId: String = Ids.randomHex(16),

    val syncHlc: String? = null
)

@Entity(
    tableName = "goals",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val targetDate: Long? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val embeddingJson: String? = null,

    @ColumnInfo(defaultValue = "''")
    val syncId: String = Ids.randomHex(16),

    val syncHlc: String? = null
)

@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val text: String,
    val feedback: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val embeddingJson: String? = null,

    @ColumnInfo(defaultValue = "''")
    val syncId: String = Ids.randomHex(16),

    val syncHlc: String? = null
)
