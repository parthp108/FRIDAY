
package com.health.friday.nutrition

import com.health.friday.data.local.Meal
import com.health.friday.data.local.MealDao
import com.health.friday.device.HealthConnectRepository
import com.health.friday.nutrition.calibration.FoodCalibrationEngine
import com.health.friday.nutrition.calibration.FoodCalibrationEntity
import com.health.friday.nutrition.calibration.scaleNutrientsJson
import com.health.friday.nutrition.calibration.scaledBy
import com.health.friday.util.DayRange
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncMutation
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.sync.cloud.toCloudSyncPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.math.roundToInt

data class LoggedFood(
    val mealId: Long,
    val displayName: String,
    val nutrition: NutritionResult,
    val calibrationMultiplier: Double = 1.0
)

data class LoggedEstimate(
    val mealId: Long,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val calibrationMultiplier: Double
)

/** Everything needed to show a correction and to undo it. */
data class MealCorrection(
    val mealId: Long,
    val mealName: String,
    val oldCalories: Int,
    val newCalories: Int,
    val oldProtein: Double,
    val oldCarbs: Double,
    val oldFat: Double,
    val oldNutrientsJson: String,
    val calibrationKey: String,
    val previousCalibration: FoodCalibrationEntity?,
    val newBias: Double,
    val correctionCount: Int
)

sealed class CorrectionOutcome {
    object NotFound : CorrectionOutcome()
    data class Rejected(val reason: String) : CorrectionOutcome()
    data class Done(val correction: MealCorrection) : CorrectionOutcome()
}

data class LogFoodsResult(
    val logged: List<LoggedFood>,
    val skipped: List<FoodItem>
)

data class DaySummary(
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val meals: List<Meal>
)

class NutritionRepository(
    private val mealDao: MealDao,
    private val nutritionProvider: NutritionProvider,
    private val healthConnectRepository: HealthConnectRepository,
    private val calibrationEngine: FoodCalibrationEngine,
    private val cloudSyncWriter: AndroidCloudSyncWriter? = null
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayMeals(): Flow<List<Meal>> {
        return DayRange.todayFlow().flatMapLatest { range ->
            mealDao.getMealsForDay(
                startOfDay = range.first,
                endOfDay = range.second
            )
        }
    }

    fun observeMealsForDay(dayStart: Long): Flow<List<Meal>> {
        return mealDao.getMealsForDay(
            startOfDay = dayStart,
            endOfDay = DayRange.shiftDays(dayStart, 1)
        )
    }

    suspend fun searchUsdaFoods(query: String, mealType: String = ""): List<UsdaFoodItem> {
        return (nutritionProvider as? LocalNutritionProvider)?.searchFoods(query, mealType = mealType) ?: emptyList()
    }

    suspend fun previewNutrition(foodName: String, quantity: Double, unit: String): NutritionResult? {
        return nutritionProvider.getNutrition(FoodItem(name = foodName, quantity = quantity, unit = unit))
    }

    suspend fun logFoods(
        foods: List<FoodItem>,
        mealType: String,
        timestamp: Long = System.currentTimeMillis()
    ): LogFoodsResult {
        val logged = mutableListOf<LoggedFood>()
        val skipped = mutableListOf<FoodItem>()

        for (food in foods) {
            val rawNutrition = nutritionProvider.getNutrition(food)

            if (rawNutrition == null) {
                skipped.add(food)
                continue
            }

            // Scale by what FRIDAY has learned from the user's corrections of this food.
            val multiplier = calibrationEngine.multiplierFor(food.name)

            val nutrition =
                if (multiplier == 1.0) rawNutrition else rawNutrition.scaledBy(multiplier)

            val displayName = displayName(food)

            val meal = Meal(
                name = displayName,
                mealType = mealType,
                calories = nutrition.calories.roundToInt(),
                protein = nutrition.protein,
                carbohydrates = nutrition.carbohydrates,
                fat = nutrition.fat,
                timestamp = timestamp,
                nutrientsJson = nutrition.toNutrientsJson()
            )

            val mealId = insertMealWithSync(meal)

            try {
                healthConnectRepository.writeMeal(
                    mealId = mealId,
                    name = displayName,
                    mealType = mealType,
                    calories = nutrition.calories.roundToInt(),
                    protein = nutrition.protein,
                    carbohydrates = nutrition.carbohydrates,
                    fat = nutrition.fat,
                    timestamp = timestamp
                )
            } catch (_: Exception) {
                // FRIDAY's local entry remains the source of truth.
            }

            logged.add(
                LoggedFood(
                    mealId = mealId,
                    displayName = displayName,
                    nutrition = nutrition,
                    calibrationMultiplier = multiplier
                )
            )
        }

        return LogFoodsResult(
            logged = logged,
            skipped = skipped
        )
    }

    suspend fun logEstimated(
        name: String,
        mealType: String,
        calories: Int,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        timestamp: Long
    ): LoggedEstimate {
        // Scale by what FRIDAY has learned from corrections of this dish.
        val multiplier = calibrationEngine.multiplierFor(name)

        val finalCalories =
            if (multiplier == 1.0) calories else (calories * multiplier).roundToInt()
        val finalProtein = protein * multiplier
        val finalCarbs = carbohydrates * multiplier
        val finalFat = fat * multiplier

        val meal = Meal(
            name = name,
            mealType = mealType,
            calories = finalCalories,
            protein = finalProtein,
            carbohydrates = finalCarbs,
            fat = finalFat,
            timestamp = timestamp,
            isEstimated = true
        )

        val mealId = insertMealWithSync(meal)

        try {
            healthConnectRepository.writeMeal(
                mealId = mealId,
                name = name,
                mealType = mealType,
                calories = finalCalories,
                protein = finalProtein,
                carbohydrates = finalCarbs,
                fat = finalFat,
                timestamp = timestamp
            )
        } catch (_: Exception) {
            // Keep the estimated meal in FRIDAY even if Health Connect fails.
        }

        return LoggedEstimate(
            mealId = mealId,
            calories = finalCalories,
            protein = finalProtein,
            carbohydrates = finalCarbs,
            fat = finalFat,
            calibrationMultiplier = multiplier
        )
    }

    suspend fun getTodaySummary(): DaySummary {
        val range = DayRange.today()

        val meals = mealDao.getMealsForDay(
            startOfDay = range.first,
            endOfDay = range.second
        ).first()

        return DaySummary(
            calories = meals.sumOf { it.calories },
            protein = meals.sumOf { it.protein },
            carbohydrates = meals.sumOf { it.carbohydrates },
            fat = meals.sumOf { it.fat },
            meals = meals
        )
    }

    suspend fun getMealsBetween(
        startMillis: Long,
        endMillis: Long
    ): List<Meal> {
        return mealDao.getMealsForDay(
            startOfDay = startMillis,
            endOfDay = endMillis
        ).first()
    }

    suspend fun deleteMeal(meal: Meal) {
        deleteMealWithSync(meal)

        try {
            healthConnectRepository.deleteMeal(mealId = meal.id)
        } catch (_: Exception) {
            // Local deletion remains successful.
        }
    }

    /**
     * Corrects the calories of a logged meal to what the user says is real.
     * Macros and nutrient amounts are scaled by the same ratio, Health Connect
     * is rewritten, and the correction teaches the calibration engine.
     */
    suspend fun correctMealCalories(
        foodQuery: String?,
        actualKcal: Int
    ): CorrectionOutcome {
        val todayStart = DayRange.today().first

        // Newest first: yesterday and today.
        val candidates = mealDao.getMealsForDay(
            DayRange.shiftDays(todayStart, -1),
            DayRange.shiftDays(todayStart, 1)
        ).first()

        val query = foodQuery?.trim().orEmpty().lowercase()

        val target: Meal? =
            if (query.isEmpty()) {
                candidates.firstOrNull()
            } else {
                candidates.firstOrNull { it.name.lowercase().contains(query) }
            }

        if (target == null) {
            return CorrectionOutcome.NotFound
        }

        if (target.calories <= 0) {
            return CorrectionOutcome.Rejected(
                "${target.name} has no calories recorded, so there is nothing to scale."
            )
        }

        if (target.calories == actualKcal) {
            return CorrectionOutcome.Rejected(
                "${target.name} is already logged at $actualKcal kcal."
            )
        }

        val ratio = actualKcal.toDouble() / target.calories

        if (ratio < 0.1 || ratio > 10.0) {
            return CorrectionOutcome.Rejected(
                "$actualKcal kcal is more than 10 times away from the logged " +
                        "${target.calories} kcal for ${target.name}. That looks like a typo."
            )
        }

        val updated = target.copy(
            calories = actualKcal,
            protein = target.protein * ratio,
            carbohydrates = target.carbohydrates * ratio,
            fat = target.fat * ratio,
            nutrientsJson = target.nutrientsJson.scaleNutrientsJson(ratio)
        )

        updateMealWithSync(updated)
        rewriteInHealthConnect(updated)

        // The calories the user saw (target.calories) are the "shown" estimate.
        val previous = calibrationEngine.recordCorrection(
            foodName = target.name,
            shownKcal = target.calories.toDouble(),
            actualKcal = actualKcal.toDouble()
        )

        val state = calibrationEngine.stateFor(target.name)

        return CorrectionOutcome.Done(
            MealCorrection(
                mealId = target.id,
                mealName = target.name,
                oldCalories = target.calories,
                newCalories = actualKcal,
                oldProtein = target.protein,
                oldCarbs = target.carbohydrates,
                oldFat = target.fat,
                oldNutrientsJson = target.nutrientsJson,
                calibrationKey = calibrationEngine.keyFor(target.name),
                previousCalibration = previous,
                newBias = state?.biasMultiplier ?: 1.0,
                correctionCount = state?.correctionCount ?: 1
            )
        )
    }

    /**
     * Undo of a correction: puts the meal back and reverts the calibration.
     * Returns the meal name, or null if the meal no longer exists
     * (the calibration is still reverted).
     */
    suspend fun restoreCorrectedMeal(
        mealId: Long,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        nutrientsJson: String,
        calibrationKey: String,
        previousCalibration: FoodCalibrationEntity?
    ): String? {
        calibrationEngine.restore(calibrationKey, previousCalibration)

        val meal = mealDao.getMealsByIds(listOf(mealId)).firstOrNull() ?: return null

        val restored = meal.copy(
            calories = calories,
            protein = protein,
            carbohydrates = carbs,
            fat = fat,
            nutrientsJson = nutrientsJson
        )

        updateMealWithSync(restored)
        rewriteInHealthConnect(restored)

        return meal.name
    }

    private suspend fun rewriteInHealthConnect(meal: Meal) {
        try {
            healthConnectRepository.deleteMeal(mealId = meal.id)

            healthConnectRepository.writeMeal(
                mealId = meal.id,
                name = meal.name,
                mealType = meal.mealType,
                calories = meal.calories,
                protein = meal.protein,
                carbohydrates = meal.carbohydrates,
                fat = meal.fat,
                timestamp = meal.timestamp
            )
        } catch (_: Exception) {
            // The local record stays the source of truth.
        }
    }

    /** Removes meals by id (local + Health Connect). Returns how many existed. */
    suspend fun deleteMealsByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0

        val meals = mealDao.getMealsByIds(ids)

        for (meal in meals) {
            deleteMeal(meal)
        }

        return meals.size
    }

    private fun displayName(food: FoodItem): String {
        val name = food.name.replaceFirstChar { it.uppercase() }
        val quantity = formatQuantity(food.quantity)

        if (food.unit == "piece") {
            return "$name × $quantity"
        }

        val unit = if (food.quantity == 1.0) {
            food.unit
        } else {
            food.unit + "s"
        }

        return "$name × $quantity $unit"
    }

    private fun formatQuantity(quantity: Double): String {
        return if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            quantity.toString()
        }
    }

    private suspend fun insertMealWithSync(meal: Meal): Long {
        val writer = cloudSyncWriter
            ?: return mealDao.insertMeal(meal)

        return writer.upsert(
            entityType = CloudSyncEntityTypes.MEAL,
            entityId = meal.syncId
        ) { hlc ->
            val stamped = meal.copy(syncHlc = hlc)
            val id = mealDao.insertMeal(stamped)

            CloudSyncMutation(
                value = id,
                payload = stamped.toCloudSyncPayload()
            )
        }
    }

    private suspend fun updateMealWithSync(meal: Meal) {
        val writer = cloudSyncWriter

        if (writer == null) {
            mealDao.updateMeal(meal)
            return
        }

        writer.upsert(
            entityType = CloudSyncEntityTypes.MEAL,
            entityId = meal.syncId
        ) { hlc ->
            val stamped = meal.copy(syncHlc = hlc)
            mealDao.updateMeal(stamped)

            CloudSyncMutation(
                value = Unit,
                payload = stamped.toCloudSyncPayload()
            )
        }
    }

    private suspend fun deleteMealWithSync(meal: Meal) {
        val writer = cloudSyncWriter

        if (writer == null) {
            mealDao.deleteMeal(meal)
            return
        }

        writer.delete(
            entityType = CloudSyncEntityTypes.MEAL,
            entityId = meal.syncId
        ) {
            mealDao.deleteMeal(meal)
        }
    }
}
