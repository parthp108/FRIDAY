package com.health.friday.nutrition

data class FoodParseResult(
    val foods: List<FoodItem>,
    val unknownText: String
)