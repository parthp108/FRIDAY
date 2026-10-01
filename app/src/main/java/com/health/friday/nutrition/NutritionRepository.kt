package com.health.friday.nutrition

import com.health.friday.data.local.Meal
import com.health.friday.data.local.MealDao
import kotlinx.coroutines.flow.Flow

class NutritionRepository(
    private val mealDao: MealDao,
    private val nutritionProvider: NutritionProvider
) {

    fun getTodayMeals(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<Meal>> {
        return mealDao.getMealsForDay(
            startOfDay = startOfDay,
            endOfDay = endOfDay
        )
    }

    suspend fun addMeal(
        meal: Meal
    ) {
        mealDao.insertMeal(meal)
    }

    suspend fun addFood(
        food: FoodItem,
        mealType: String
    ) {

        val nutrition =
            nutritionProvider.getNutrition(food)
                ?: return

        val meal =
            Meal(
                name = food.name,
                mealType = mealType,
                calories = nutrition.calories.toInt(),
                protein = nutrition.protein,
                carbohydrates = nutrition.carbohydrates,
                fat = nutrition.fat
            )

        mealDao.insertMeal(meal)
    }

    suspend fun deleteMeal(
        meal: Meal
    ) {
        mealDao.deleteMeal(meal)
    }
}