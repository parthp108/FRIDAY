
package com.health.friday.nutrition

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LocalNutritionProvider(
    context: Context
) : NutritionProvider {

    private val usdaDatabase = UsdaFoodDatabase(context)

    private data class FoodCandidate(
        val fdcId: Long,
        val description: String,
        val dataType: String
    )

    private data class PortionCandidate(
        val amount: Double,
        val gramWeight: Double,
        val description: String,
        val modifier: String,
        val unitName: String
    )

    override suspend fun getNutrition(
        food: FoodItem
    ): NutritionResult? = withContext(Dispatchers.IO) {
        if (food.quantity <= 0.0 || food.name.isBlank()) {
            return@withContext null
        }

        try {
            val db = usdaDatabase.database()
            val candidate = resolveFood(db, food)

            if (candidate == null) {
                android.util.Log.w(
                    "FRIDAY_USDA",
                    "No USDA food match for '${food.name}'"
                )
                return@withContext null
            }

            val gramsPerUnit =
                if (
                    food.unit.equals("g", ignoreCase = true) ||
                    food.unit.equals("gram", ignoreCase = true) ||
                    food.unit.equals("grams", ignoreCase = true)
                ) {
                    1.0
                } else {
                    resolveGramsPerUnit(
                        db = db,
                        food = food,
                        fdcId = candidate.fdcId
                    ) ?: run {
                        android.util.Log.w(
                            "FRIDAY_USDA",
                            "No valid USDA portion for '${food.name}', " +
                                    "unit='${food.unit}', fdcId=${candidate.fdcId}"
                        )
                        return@withContext null
                    }
                }

            val totalGrams = gramsPerUnit * food.quantity

            if (totalGrams <= 0.0) {
                return@withContext null
            }

            val nutrients = loadNutrients(
                db = db,
                fdcId = candidate.fdcId,
                totalGrams = totalGrams
            )

            val calories = nutrients.firstOrNull {
                it.name.equals("Energy", ignoreCase = true) &&
                        it.unit.equals("kcal", ignoreCase = true)
            }?.amount ?: return@withContext null

            val protein = nutrients.firstOrNull {
                it.name.equals("Protein", ignoreCase = true)
            }?.amount ?: 0.0

            val carbohydrates = nutrients.firstOrNull {
                it.name.equals(
                    "Carbohydrate, by difference",
                    ignoreCase = true
                )
            }?.amount ?: 0.0

            val fat = nutrients.firstOrNull {
                it.name.equals(
                    "Total lipid (fat)",
                    ignoreCase = true
                )
            }?.amount ?: 0.0

            android.util.Log.i(
                "FRIDAY_USDA",
                "Matched '${food.name}' to '${candidate.description}' " +
                        "(fdcId=${candidate.fdcId}, " +
                        "quantity=${food.quantity}, unit=${food.unit}, " +
                        "gramsPerUnit=$gramsPerUnit, totalGrams=$totalGrams)"
            )

            NutritionResult(
                calories = calories,
                protein = protein,
                carbohydrates = carbohydrates,
                fat = fat,
                nutrients = nutrients,
                totalGrams = totalGrams
            )
        } catch (exception: Exception) {
            android.util.Log.e(
                "FRIDAY_USDA",
                "USDA lookup failed for '${food.name}', " +
                        "quantity=${food.quantity}, unit=${food.unit}",
                exception
            )
            return@withContext null
        }
    }

    private fun resolveFood(
        db: SQLiteDatabase,
        food: FoodItem
    ): FoodCandidate? {
        val tokens = food.name
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .distinct()

        if (tokens.isEmpty()) return null

        val conditions = tokens.joinToString(" AND ") {
            "LOWER(f.description) LIKE ?"
        }

        val query = """
            SELECT
                f.fdc_id,
                f.description,
                f.data_type
            FROM food f
            WHERE $conditions
            LIMIT 1000
        """.trimIndent()

        val arguments = tokens.map { token ->
            "%$token%"
        }.toTypedArray()

        val candidates = mutableListOf<FoodCandidate>()

        db.rawQuery(query, arguments).use { cursor ->
            while (cursor.moveToNext()) {
                candidates += FoodCandidate(
                    fdcId = cursor.getLong(0),
                    description = cursor.getString(1).orEmpty(),
                    dataType = cursor.getString(2).orEmpty()
                )
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<FoodCandidate> {
                    scoreCandidate(it, food)
                }.thenBy {
                    it.description.length
                }.thenBy {
                    it.description
                }
            )
            .firstOrNull()
    }

    private fun scoreCandidate(
        candidate: FoodCandidate,
        food: FoodItem
    ): Int {
        val description = candidate.description.lowercase()
        val query = food.name.lowercase().trim()
        val firstPart = description.substringBefore(",").trim()

        var score = 0

        if (firstPart == query) {
            score += 1000
        } else if (firstPart.startsWith("$query ")) {
            score += 450
        }

        if (description.contains(query)) {
            score += 100
        }

        score += when (candidate.dataType.lowercase()) {
            "foundation_food" -> 40
            "sr_legacy_food" -> 30
            "survey_fndds_food" -> 20
            else -> 0
        }

        val compositeTerms = listOf(
            "sandwich", "salad", "soup", "casserole",
            "burrito", "pizza", "cake", "roll", "babyfood"
        )

        if (compositeTerms.any(description::contains)) {
            score -= 250
        }

        when (query) {
            "egg" -> {
                if (food.unit.equals("piece", true)) {
                    if ("hard-boiled" in description) score += 180
                    else if ("boiled" in description) score += 140
                    else if ("cooked" in description) score += 100
                    else if ("raw" in description) score -= 30
                }
            }

            "rice" -> {
                if (
                    food.unit.equals("cup", true) &&
                    "cooked" in description
                ) {
                    score += 150
                }
            }

            "toast" -> {
                if ("toasted" in description || "toast" in description) {
                    score += 180
                }
            }

            "banana", "apple", "orange" -> {
                if ("raw" in description) score += 80
            }
        }

        return score
    }

    private fun resolveGramsPerUnit(
        db: SQLiteDatabase,
        food: FoodItem,
        fdcId: Long
    ): Double? {
        val portions = mutableListOf<PortionCandidate>()

        db.rawQuery(
            """
            SELECT
                fp.amount,
                fp.gram_weight,
                fp.portion_description,
                fp.modifier,
                COALESCE(mu.name, '')
            FROM food_portion fp
            LEFT JOIN measure_unit mu
                ON mu.id = fp.measure_unit_id
            WHERE fp.fdc_id = ?
            """.trimIndent(),
            arrayOf(fdcId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(0) || cursor.isNull(1)) continue

                val amount = cursor.getDouble(0)
                val gramWeight = cursor.getDouble(1)

                if (amount <= 0.0 || gramWeight <= 0.0) continue

                portions += PortionCandidate(
                    amount = amount,
                    gramWeight = gramWeight,
                    description = cursor.getString(2).orEmpty(),
                    modifier = cursor.getString(3).orEmpty(),
                    unitName = cursor.getString(4).orEmpty()
                )
            }
        }

        val ranked = portions
            .map { portion ->
                portion to scorePortion(food, portion)
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<PortionCandidate, Int>> {
                    it.second
                }.thenBy {
                    abs(it.first.amount - 1.0)
                }

            )

        ranked.forEach { (portion, score) ->
            android.util.Log.d(
                "FRIDAY_USDA",
                "Portion candidate: description='${portion.description}', " +
                        "modifier='${portion.modifier}', " +
                        "unit='${portion.unitName}', " +
                        "amount=${portion.amount}, " +
                        "gramWeight=${portion.gramWeight}, score=$score"
            )
        }

        val best = ranked.firstOrNull()?.first ?: return null

        val gramsPerUnit = best.gramWeight / best.amount

        android.util.Log.i(
            "FRIDAY_USDA",
            "Selected portion for '${food.name}': " +
                    "${best.description}, ${best.modifier}, " +
                    "${best.unitName}; gramsPerUnit=$gramsPerUnit"
        )

        return gramsPerUnit
    }



    private fun scorePortion(
        food: FoodItem,
        portion: PortionCandidate
    ): Int {
        val description = portion.description.lowercase()
        val modifier = portion.modifier.lowercase()
        val unit = portion.unitName.lowercase()
        val combined = "$description $modifier $unit"
        val oneUnit = abs(portion.amount - 1.0) < 0.001

        if (food.unit.equals("piece", ignoreCase = true)) {
            val normalizedName =
                food.name.lowercase().trim().trimEnd('s')

            if (normalizedName == "egg") {
                val gramsPerUnit =
                    portion.gramWeight / portion.amount

                // Reject cup, tablespoon, and implausible egg weights.
                if (
                    "cup" in combined ||
                    "tbsp" in combined ||
                    gramsPerUnit !in 25.0..90.0
                ) {
                    return 0
                }

                return when {
                    "large" in modifier && oneUnit -> 1300
                    "extra large" in modifier && oneUnit -> 1250
                    "jumbo" in modifier && oneUnit -> 1200
                    "medium" in modifier && oneUnit -> 1150
                    "small" in modifier && oneUnit -> 1100
                    oneUnit -> 1000
                    else -> 0
                }
            }
        }

        return when (food.unit.lowercase()) {
            "piece" -> when {
                "medium" in combined && oneUnit -> 950
                "piece" in combined && oneUnit -> 900
                "fruit" in unit && oneUnit -> 850
                oneUnit -> 300
                else -> 0
            }

            "slice" -> when {
                "slice" in combined && oneUnit -> 1000
                "slice" in combined -> 800
                else -> 0
            }

            "cup" -> when {
                "cup" in unit && oneUnit -> 1000
                "cup" in combined && oneUnit -> 900
                "cup" in combined -> 750
                else -> 0
            }

            else -> 0
        }
    }


    private fun loadNutrients(
        db: SQLiteDatabase,
        fdcId: Long,
        totalGrams: Double
    ): List<NutrientAmount> {
        val nutrients = mutableListOf<NutrientAmount>()

        db.rawQuery(
            """
            SELECT
                n.id,
                n.name,
                n.unit_name,
                fn.amount
            FROM food_nutrient fn
            JOIN nutrient n ON n.id = fn.nutrient_id
            WHERE fn.fdc_id = ?
              AND fn.amount IS NOT NULL
            """.trimIndent(),
            arrayOf(fdcId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val amountPer100g = cursor.getDouble(3)

                nutrients += NutrientAmount(
                    id = cursor.getInt(0),
                    name = cursor.getString(1).orEmpty(),
                    unit = cursor.getString(2).orEmpty(),
                    amount = amountPer100g * totalGrams / 100.0
                )
            }
        }

        return nutrients
    }
}
