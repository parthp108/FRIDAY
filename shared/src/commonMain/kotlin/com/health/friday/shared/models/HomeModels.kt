package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class HomeDashboardSnapshot(
    val syncTimestamp: Long = 0L,
    val activeSignals: DailySignalDto = DailySignalDto(),
    val todayNutrition: DailyNutritionSummaryDto = DailyNutritionSummaryDto(),
    val pendingTasks: List<FridayTaskDto> = emptyList(),
    val upcomingReminders: List<FridayReminderDto> = emptyList(),
    val recentAnomalies: List<HealthAnomalyDto> = emptyList()
)
