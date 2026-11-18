package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.health.friday.shared.protocol.Ids

@Entity(
    tableName = "meals",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class Meal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val mealType: String,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbohydrates: Double = 0.0,
    val fat: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "0")
    val isEstimated: Boolean = false,

    @ColumnInfo(defaultValue = "'[]'")
    val nutrientsJson: String = "[]",

    @ColumnInfo(defaultValue = "''")
    val syncId: String = Ids.randomHex(16),

    val syncHlc: String? = null
)
