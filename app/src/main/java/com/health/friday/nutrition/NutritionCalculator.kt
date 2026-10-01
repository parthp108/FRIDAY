package com.health.friday.nutrition

class NutritionCalculator(
    private val localProvider: NutritionProvider,
    private val remoteProvider: NutritionProvider
) {

    fun calculate(
        foods: List<FoodItem>
    ): NutritionResult {

        var calories = 0.0
        var protein = 0.0
        var carbohydrates = 0.0
        var fat = 0.0

        for (food in foods) {

            val nutrition =
                localProvider.getNutrition(food)
                    ?: remoteProvider.getNutrition(food)
                    ?: continue

            calories += nutrition.calories
            protein += nutrition.protein
            carbohydrates += nutrition.carbohydrates
            fat += nutrition.fat
        }

        return NutritionResult(
            calories = calories,
            protein = protein,
            carbohydrates = carbohydrates,
            fat = fat
        )
    }
}