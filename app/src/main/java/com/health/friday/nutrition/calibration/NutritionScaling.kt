package com.health.friday.nutrition.calibration

import com.health.friday.nutrition.NutritionResult
import org.json.JSONArray

/** Scales calories, macros, every nutrient and the gram weight by one factor. */
fun NutritionResult.scaledBy(factor: Double): NutritionResult =
    copy(
        calories = calories * factor,
        protein = protein * factor,
        carbohydrates = carbohydrates * factor,
        fat = fat * factor,
        nutrients = nutrients.map { it.copy(amount = it.amount * factor) },
        totalGrams = totalGrams?.times(factor)
    )

/** Scales the "amount" of every entry in a meal's nutrientsJson. Returns the input unchanged if it can't be read. */
fun String.scaleNutrientsJson(factor: Double): String =
    try {
        val array = JSONArray(this)

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val amount = item.optDouble("amount", Double.NaN)

            if (amount.isFinite()) {
                item.put("amount", amount * factor)
            }
        }

        array.toString()
    } catch (_: Exception) {
        this
    }
