package com.health.friday.nutrition

class LocalNutritionProvider : NutritionProvider {

    private data class FoodNutrition(
        val calories: Double,
        val protein: Double,
        val carbohydrates: Double,
        val fat: Double
    )

    private val foods = mapOf(

        "egg" to FoodNutrition(
            calories = 78.0,
            protein = 6.3,
            carbohydrates = 0.6,
            fat = 5.3
        ),

        "toast" to FoodNutrition(
            calories = 75.0,
            protein = 3.0,
            carbohydrates = 13.0,
            fat = 1.0
        ),

        "banana" to FoodNutrition(
            calories = 105.0,
            protein = 1.3,
            carbohydrates = 27.0,
            fat = 0.4
        ),

        "apple" to FoodNutrition(
            calories = 95.0,
            protein = 0.5,
            carbohydrates = 25.0,
            fat = 0.3
        ),

        "orange" to FoodNutrition(
            calories = 62.0,
            protein = 1.2,
            carbohydrates = 15.4,
            fat = 0.2
        ),

        "rice" to FoodNutrition(
            calories = 205.0,
            protein = 4.3,
            carbohydrates = 44.5,
            fat = 0.4
        ),

        "milk" to FoodNutrition(
            calories = 122.0,
            protein = 8.0,
            carbohydrates = 12.0,
            fat = 4.8
        )
    )

    override fun getNutrition(
        food: FoodItem
    ): NutritionResult? {

        val nutrition =
            foods[food.name.lowercase()]
                ?: return null

        return NutritionResult(
            calories =
                nutrition.calories * food.quantity,

            protein =
                nutrition.protein * food.quantity,

            carbohydrates =
                nutrition.carbohydrates * food.quantity,

            fat =
                nutrition.fat * food.quantity
        )
    }
}