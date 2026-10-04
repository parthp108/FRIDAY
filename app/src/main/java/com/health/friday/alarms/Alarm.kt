
package com.health.friday.alarms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timeMillis: Long,
    val enabled: Boolean = true,
    val repeatDaily: Boolean = false
)

data class AlarmTrigger(
    val alarmId: Long,
    val title: String,
    val repeatDaily: Boolean
)

