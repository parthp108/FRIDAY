package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class FridayMealDto(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
    val sugar: Float = 0f,
    val timestamp: Long = 0L // Precise time the meal was logged
)

@Serializable
data class HydrationLogDto(
    val id: String,
    val amountMl: Int,
    val timestamp: Long
)

@Serializable
data class DailyNutritionSummaryDto(
    val date: String = "",
    val totalCalories: Int = 0,
    val totalProtein: Float = 0f,
    val totalCarbs: Float = 0f,
    val totalFat: Float = 0f,
    val totalWaterMl: Int = 0,
    val calorieGoal: Int = 2000,
    val waterGoalMl: Int = 2500
)
