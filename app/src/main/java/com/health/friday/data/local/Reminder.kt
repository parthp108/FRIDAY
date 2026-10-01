package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val timeMillis: Long,

    val enabled: Boolean = true,

    val repeatDaily: Boolean = true
)