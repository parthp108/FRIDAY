package com.health.friday.nutrition

import com.health.friday.data.local.Meal
import com.health.friday.data.local.MealDao
import com.health.friday.device.HealthConnectRepository
import com.health.friday.util.DayRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.math.roundToInt

data class LoggedFood(
    val displayName: String,
    val nutrition: NutritionResult
)

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
    private val healthConnectRepository: HealthConnectRepository
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

    fun observeMealsForDay(
        dayStart: Long
    ): Flow<List<Meal>> {
        return mealDao.getMealsForDay(
            startOfDay = dayStart,
            endOfDay = DayRange.shiftDays(dayStart, 1)
        )
    }

    suspend fun logFoods(
        foods: List<FoodItem>,
        mealType: String,
        timestamp: Long = System.currentTimeMillis()
    ): LogFoodsResult {

        val logged = mutableListOf<LoggedFood>()
        val skipped = mutableListOf<FoodItem>()

        for (food in foods) {

            val nutrition =
                nutritionProvider.getNutrition(food)

            if (nutrition == null) {
                skipped.add(food)
                continue
            }

            val displayName =
                displayName(food)

            val meal =
                Meal(
                    name = displayName,
                    mealType = mealType,
                    calories = nutrition.calories.roundToInt(),
                    protein = nutrition.protein,
                    carbohydrates = nutrition.carbohydrates,
                    fat = nutrition.fat,
                    timestamp = timestamp
                )

            val mealId =
                mealDao.insertMeal(meal)

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
                // Keep the FRIDAY entry even if Health Connect
                // is temporarily unavailable.
            }

            logged.add(
                LoggedFood(
                    displayName = displayName,
                    nutrition = nutrition
                )
            )
        }

        return LogFoodsResult(
            logged = logged,
            skipped = skipped
        )
    }

    // Saves a meal whose nutrition numbers were estimated by the AI.
    // The meal is still stored locally as the source of truth and
    // is also written to Health Connect.
    suspend fun logEstimated(
        name: String,
        mealType: String,
        calories: Int,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        timestamp: Long
    ) {

        val meal =
            Meal(
                name = name,
                mealType = mealType,
                calories = calories,
                protein = protein,
                carbohydrates = carbohydrates,
                fat = fat,
                timestamp = timestamp,
                isEstimated = true
            )

        val mealId =
            mealDao.insertMeal(meal)

        try {
            healthConnectRepository.writeMeal(
                mealId = mealId,
                name = name,
                mealType = mealType,
                calories = calories,
                protein = protein,
                carbohydrates = carbohydrates,
                fat = fat,
                timestamp = timestamp
            )
        } catch (_: Exception) {
            // Keep the FRIDAY entry even if Health Connect
            // is temporarily unavailable.
        }
    }

    suspend fun getTodaySummary(): DaySummary {

        val range = DayRange.today()

        val meals =
            mealDao
                .getMealsForDay(
                    startOfDay = range.first,
                    endOfDay = range.second
                )
                .first()

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

        return mealDao
            .getMealsForDay(
                startOfDay = startMillis,
                endOfDay = endMillis
            )
            .first()
    }

    suspend fun deleteMeal(
        meal: Meal
    ) {

        mealDao.deleteMeal(meal)

        try {
            healthConnectRepository.deleteMeal(
                mealId = meal.id
            )
        } catch (_: Exception) {
            // Local FRIDAY deletion remains successful.
        }
    }

    private fun displayName(
        food: FoodItem
    ): String {

        val name =
            food.name.replaceFirstChar {
                it.uppercase()
            }

        val quantity =
            formatQuantity(food.quantity)

        if (food.unit == "piece") {
            return "$name × $quantity"
        }

        val unit =
            if (food.quantity == 1.0) {
                food.unit
            } else {
                food.unit + "s"
            }

        return "$name × $quantity $unit"
    }

    private fun formatQuantity(
        quantity: Double
    ): String {

        return if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            quantity.toString()
        }
    }
}