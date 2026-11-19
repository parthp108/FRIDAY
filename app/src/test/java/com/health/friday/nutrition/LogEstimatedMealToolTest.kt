package com.health.friday.nutrition

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.actions.ActionRecorder
import com.health.friday.data.local.ActionLogDao
import com.health.friday.data.local.ActionLogEntity
import com.health.friday.data.local.Meal
import com.health.friday.data.local.MealDao
import com.health.friday.device.HealthConnectRepository
import com.health.friday.nutrition.calibration.FoodCalibrationDao
import com.health.friday.nutrition.calibration.FoodCalibrationEngine
import com.health.friday.nutrition.calibration.FoodCalibrationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMealDao : MealDao {
    private val meals = mutableListOf<Meal>()
    private var idCounter = 1L

    override suspend fun getBySyncId(syncId: String): Meal? = meals.find { it.syncId == syncId }

    override suspend fun deleteBySyncId(syncId: String): Int {
        val count = meals.count { it.syncId == syncId }
        meals.removeAll { it.syncId == syncId }
        return count
    }

    override suspend fun insertMeal(meal: Meal): Long {
        val inserted = meal.copy(id = idCounter++)
        meals.add(inserted)
        return inserted.id
    }

    override suspend fun updateMeal(meal: Meal) {
        val index = meals.indexOfFirst { it.id == meal.id }
        if (index >= 0) meals[index] = meal
    }

    override suspend fun deleteMeal(meal: Meal) {
        meals.removeAll { it.id == meal.id }
    }

    override fun getMealsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Meal>> {
        return flowOf(meals.filter { it.timestamp in startOfDay until endOfDay })
    }

    override suspend fun getMealsByIds(ids: List<Long>): List<Meal> {
        return meals.filter { it.id in ids }
    }

    override fun getAllMeals(): Flow<List<Meal>> {
        return flowOf(meals)
    }

    override suspend fun getMealsBetween(start: Long, end: Long): List<Meal> {
        return meals.filter { it.timestamp in start until end }
    }
}

class FakeActionLogDao : ActionLogDao {
    override suspend fun insertAction(action: ActionLogEntity): Long = 1L
    override suspend fun getLastAction(): ActionLogEntity? = null
    override suspend fun getLastUndoableAction(): ActionLogEntity? = null
    override fun getRecentActionsFlow(): Flow<List<ActionLogEntity>> = flowOf(emptyList())
    override suspend fun deleteAction(actionId: Long) {}
    override suspend fun deleteOlderThan(cutoffMillis: Long) {}
    override suspend fun clearAll() {}
}

class LogEstimatedMealToolTest {

    private val fakeMealDao = FakeMealDao()
    private val fakeCalibrationDao = object : FoodCalibrationDao {
        override suspend fun getCalibration(name: String): FoodCalibrationEntity? = null
        override suspend fun insertOrUpdate(calibration: FoodCalibrationEntity) {}
        override suspend fun deleteCalibration(name: String) {}
    }
    private val calibrationEngine = FoodCalibrationEngine(fakeCalibrationDao)

    private val fakeContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private val repository = NutritionRepository(
        mealDao = fakeMealDao,
        nutritionProvider = object : NutritionProvider {
            override suspend fun getNutrition(food: FoodItem): NutritionResult? = null
        },
        healthConnectRepository = HealthConnectRepository(fakeContext),
        calibrationEngine = calibrationEngine
    )

    private val tool = LogEstimatedMealTool(
        repository = repository,
        actionRecorder = ActionRecorder(FakeActionLogDao())
    )

    @Test
    fun testLogEstimatedMealSuccess() = runBlocking {
        val args = mapOf(
            "name" to "Triple-decker McDouble bypass burger",
            "calories" to "1200",
            "protein_g" to "60",
            "carbs_g" to "70",
            "fat_g" to "75"
        )

        val result = tool.execute(args)

        // Must state ESTIMATE, not measured
        assertTrue(result.contains("ESTIMATE, not measured"))
        assertTrue(result.contains("Triple-decker McDouble bypass burger"))
        assertTrue(result.contains("1200 kcal"))
    }

    @Test
    fun testLogEstimatedMealMacroInconsistencyRejection() = runBlocking {
        // Macros: 4*10 + 4*10 + 9*10 = 170 kcal, but calories claimed is 1000 kcal!
        val args = mapOf(
            "name" to "Inconsistent Burger",
            "calories" to "1000",
            "protein_g" to "10",
            "carbs_g" to "10",
            "fat_g" to "10"
        )

        val result = tool.execute(args)

        // Should refuse to log inconsistent macros
        assertTrue(result.contains("Nothing logged") || result.contains("adds up to"))
    }
}
