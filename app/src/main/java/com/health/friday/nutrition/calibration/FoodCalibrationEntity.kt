package com.health.friday.nutrition.calibration

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_calibration")
data class FoodCalibrationEntity(
    @PrimaryKey val canonicalName: String,
    val biasMultiplier: Double = 1.0,
    val correctionCount: Int = 0,
    val lastCorrectedAt: Long = System.currentTimeMillis()
)