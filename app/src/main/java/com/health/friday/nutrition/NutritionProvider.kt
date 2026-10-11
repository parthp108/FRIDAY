
package com.health.friday.nutrition

interface NutritionProvider {

    suspend fun getNutrition(
        food: FoodItem
    ): NutritionResult?
}
