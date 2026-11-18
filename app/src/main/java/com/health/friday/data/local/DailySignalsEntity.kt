package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_signals")
data class DailySignalsEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val sleepMinutes: Int? = null,
    val sleepStartEpoch: Long? = null,
    val sleepEndEpoch: Long? = null,
    val steps: Int? = null,
    val restingHr: Int? = null,
    val kcal: Int? = null,
    val proteinGrams: Float? = null,
    val carbsGrams: Float? = null,
    val fatGrams: Float? = null,
    val waterMl: Int? = null,
    val lastMealHour: Float? = null, // e.g., 21.5 = 9:30 PM
    val screenTimeMinutes: Int? = null,
    val tasksCompleted: Int = 0,
    val tasksPending: Int = 0,
    val moodScore: Int? = null,       // 1 (lowest) to 5 (highest)
    val workoutMinutes: Int = 0,
    val activeCaloriesKcal: Int? = null,
    val distanceMeters: Double? = null,
    val hrvMs: Double? = null,
    val spo2Percent: Double? = null,
    val syncHlc: String? = null
)
