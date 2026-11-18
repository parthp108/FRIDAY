package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DailySignalDto(
    val date: String = "", // YYYY-MM-DD
    val heartRateBpm: Int = 0,
    val restingHeartRate: Int = 0,
    val hrv: Float = 0f,
    val stressScore: Float = 0f,
    val recoveryScore: Float = 0f,
    val stepCount: Int = 0,
    val activeEnergyBurned: Int = 0,
    val sleepDurationMinutes: Int = 0,
    val sleepScore: Int? = null,
    val waterIntakeMl: Int? = null,
    val activeMinutes: Int? = null,
    val phoneUsageMinutes: Int? = null,
    val bloodOxygenPercentage: Float = 0f,
    val timestamp: Long = 0L // Exact time of last sync
)

@Serializable
data class HealthAnomalyDto(
    val id: String,
    val type: String, // e.g., "HIGH_HR", "LOW_HRV"
    val description: String,
    val timestamp: Long
)
