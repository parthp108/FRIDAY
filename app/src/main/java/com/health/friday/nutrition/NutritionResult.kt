
package com.health.friday.nutrition

import org.json.JSONArray
import org.json.JSONObject

data class NutrientAmount(
    val id: Int,
    val name: String,
    val amount: Double,
    val unit: String
)

data class NutritionResult(
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val nutrients: List<NutrientAmount> = emptyList(),
    val totalGrams: Double? = null
)

/**
 * Stores the complete nutrient list in a JSON column on a meal.
 * Calories and macros remain in their existing Room columns.
 */
fun NutritionResult.toNutrientsJson(): String {
    val array = JSONArray()

    nutrients.forEach { nutrient ->
        array.put(
            JSONObject()
                .put("id", nutrient.id)
                .put("name", nutrient.name)
                .put("amount", nutrient.amount)
                .put("unit", nutrient.unit)
        )
    }

    return array.toString()
}
