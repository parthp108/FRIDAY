package com.health.friday.nutrition

interface NutritionProvider {

    fun getNutrition(
        food: FoodItem
    ): NutritionResult?
}