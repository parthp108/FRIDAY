
package com.health.friday.nutrition

import com.health.friday.data.local.Meal
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
 * Stores the complete nutrient list in the existing JSON column on a meal.
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

/**
 * A daily nutrient total.
 *
 * value is null when no usable values are available.
 * complete is false when at least one meal lacks this nutrient.
 *
 * Partial totals are retained for transparency, but the UI must mark
 * them as partial rather than presenting them as complete daily totals.
 */
data class DailyNutrientTotal(
    val key: String,
    val label: String,
    val unit: String,
    val value: Double?,
    val complete: Boolean
)

private data class NutrientDefinition(
    val key: String,
    val label: String,
    val unit: String,
    val names: Set<String>
)

private val nutrientDefinitions = listOf(
    NutrientDefinition(
        "fiber", "FIBER", "g",
        setOf("fiber", "dietary fiber", "fiber, total dietary")
    ),
    NutrientDefinition(
        "sugar", "SUGAR", "g",
        setOf(
            "sugar", "sugars", "sugars, total",
            "sugars, total including nlea"
        )
    ),
    NutrientDefinition(
        "saturated_fat", "SAT. FAT", "g",
        setOf(
            "fatty acids, total saturated",
            "saturated fat", "saturated fatty acids"
        )
    ),
    NutrientDefinition(
        "trans_fat", "TRANS FAT", "g",
        setOf(
            "fatty acids, total trans",
            "trans fat", "trans fatty acids"
        )
    ),
    NutrientDefinition(
        "monounsaturated_fat", "MONO FAT", "g",
        setOf(
            "fatty acids, total monounsaturated",
            "monounsaturated fat", "monounsaturated fatty acids"
        )
    ),
    NutrientDefinition(
        "polyunsaturated_fat", "POLY FAT", "g",
        setOf(
            "fatty acids, total polyunsaturated",
            "polyunsaturated fat", "polyunsaturated fatty acids"
        )
    ),
    NutrientDefinition(
        "cholesterol", "CHOLESTEROL", "mg",
        setOf("cholesterol")
    ),
    NutrientDefinition(
        "sodium", "SODIUM", "mg",
        setOf("sodium", "sodium, na")
    ),
    NutrientDefinition(
        "potassium", "POTASSIUM", "mg",
        setOf("potassium", "potassium, k")
    ),
    NutrientDefinition(
        "calcium", "CALCIUM", "mg",
        setOf("calcium", "calcium, ca")
    ),
    NutrientDefinition(
        "iron", "IRON", "mg",
        setOf("iron", "iron, fe")
    ),
    NutrientDefinition(
        "vitamin_a", "VITAMIN A", "µg",
        setOf(
            "vitamin a, rae",
            "vitamin a",
            "retinol activity equivalents"
        )
    ),
    NutrientDefinition(
        "vitamin_c", "VITAMIN C", "mg",
        setOf(
            "vitamin c, total ascorbic acid",
            "vitamin c",
            "ascorbic acid"
        )
    )
)

private fun normalizeNutrientName(name: String): String =
    name.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("""\s+"""), " ")

private fun normalizeNutrientUnit(unit: String): String =
    unit.trim()
        .lowercase(Locale.ROOT)
        .replace("μ", "µ")
        .replace("micrograms", "µg")
        .replace("microgram", "µg")
        .replace("milligrams", "mg")
        .replace("milligram", "mg")
        .replace("grams", "g")
        .replace("gram", "g")

private fun convertNutrientAmount(
    amount: Double,
    sourceUnit: String,
    targetUnit: String
): Double? {
    if (!amount.isFinite()) return null

    val source = normalizeNutrientUnit(sourceUnit)
    val target = normalizeNutrientUnit(targetUnit)

    if (source == target) return amount

    return when {
        source == "µg" && target == "mg" -> amount / 1000.0
        source == "mg" && target == "µg" -> amount * 1000.0
        source == "mg" && target == "g" -> amount / 1000.0
        source == "g" && target == "mg" -> amount * 1000.0
        else -> null
    }
}

/**
 * Safely reads the existing nutrientsJson field.
 * Malformed entries are ignored instead of crashing the screen.
 */
fun String.decodeNutrientsJson(): List<NutrientAmount> {
    return try {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val amount = item.optDouble("amount", Double.NaN)
                if (!amount.isFinite()) continue

                add(
                    NutrientAmount(
                        id = item.optInt("id", 0),
                        name = item.optString("name", ""),
                        amount = amount,
                        unit = item.optString("unit", "")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Aggregates nutrient data for the meals already loaded for a selected day.
 *
 * A meal is considered to have a nutrient only when that nutrient is present
 * in its JSON and its unit can be converted safely. Estimated meals and older
 * entries with empty nutrient JSON therefore make a total incomplete.
 */
fun List<Meal>.calculateDailyNutrientTotals(): List<DailyNutrientTotal> {
    return nutrientDefinitions.map { definition ->
        var sum = 0.0
        var foundAny = false
        var complete = isNotEmpty()

        for (meal in this) {
            val matchingAmounts = meal.nutrientsJson
                .decodeNutrientsJson()
                .filter {
                    normalizeNutrientName(it.name) in definition.names
                }

            val converted = matchingAmounts.mapNotNull {
                convertNutrientAmount(
                    amount = it.amount,
                    sourceUnit = it.unit,
                    targetUnit = definition.unit
                )
            }

            if (converted.isEmpty()) {
                complete = false
            } else {
                sum += converted.sum()
                foundAny = true
            }
        }

        DailyNutrientTotal(
            key = definition.key,
            label = definition.label,
            unit = definition.unit,
            value = if (foundAny) sum else null,
            complete = complete && foundAny
        )
    }
}
