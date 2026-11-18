package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.health.friday.shared.protocol.Ids

@Entity(
    tableName = "water_entries",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class WaterEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "''")
    val syncId: String = Ids.randomHex(16),

    val syncHlc: String? = null
)
