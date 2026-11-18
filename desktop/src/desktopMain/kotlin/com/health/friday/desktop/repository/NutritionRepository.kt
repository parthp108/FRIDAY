package com.health.friday.desktop.repository

import com.health.friday.shared.models.FridayMealDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

class NutritionRepository(private val onReadOnly: (String) -> Unit) {
    private val _meals = MutableStateFlow<List<FridayMealDto>>(emptyList())
    val meals: StateFlow<List<FridayMealDto>> = _meals

    fun setMeals(fetched: List<FridayMealDto>) {
        _meals.value = fetched
    }

    @Suppress("UNUSED_PARAMETER")
    fun logMeal(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) {
        onReadOnly("Logging a meal")
    }
}

/** The phone sends all history; "today" figures must only count today. */
fun List<FridayMealDto>.todayMeals(): List<FridayMealDto> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    val end = cal.timeInMillis
    return filter { it.timestamp in start until end }
}
