package com.health.friday.nutrition

data class FoodSearchResult(
    val name: String,
    val brand: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbohydratesPer100g: Double,
    val fatPer100g: Double
)