package com.health.friday.predictive

import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.data.local.Meal
import com.health.friday.data.local.MealDao
import com.health.friday.proactive.FakeDailySignalsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakeMealDaoForPredictive : MealDao {
    val meals = mutableListOf<Meal>()

    override suspend fun insertMeal(meal: Meal): Long = 1L
    override suspend fun updateMeal(meal: Meal) {}
    override suspend fun deleteMeal(meal: Meal) {}
    override fun getMealsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Meal>> = flowOf(meals)
    override suspend fun getMealsByIds(ids: List<Long>): List<Meal> = meals
    override fun getAllMeals(): Flow<List<Meal>> = flowOf(meals)
    override suspend fun getMealsBetween(start: Long, end: Long): List<Meal> = meals
}

class PredictiveEngineTest {

    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var fakeMealDao: FakeMealDaoForPredictive
    private lateinit var engine: PredictiveEngine

    @Before
    fun setUp() {
        fakeSignalsDao = FakeDailySignalsDao()
        fakeMealDao = FakeMealDaoForPredictive()
        engine = PredictiveEngine(fakeSignalsDao, fakeMealDao)
    }

    @Test
    fun testForecastEnergy_insufficientData_returnsUnknown() = runBlocking {
        val forecast = engine.forecastEnergy()
        assertEquals("UNKNOWN", forecast.crashRiskLevel)
    }

    @Test
    fun testForecastEnergy_withSleepDebt_calculatesRisk() = runBlocking {
        val today = LocalDate.now()

        // 5 baseline days with 8 hours (480 mins) sleep
        for (i in 1..5) {
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = today.minusDays(i.toLong()).toString(),
                    sleepMinutes = 480
                )
            )
        }

        // Today's sleep is only 5 hours (300 mins) -> 180 min sleep debt
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = today.toString(),
                sleepMinutes = 300
            )
        )

        val forecast = engine.forecastEnergy()

        // Risk should be HIGH or SEVERE due to large sleep debt
        assertTrue(forecast.crashRiskLevel == "HIGH" || forecast.crashRiskLevel == "SEVERE")
        assertNotNull(forecast.crashTimeEstimate)
        assertTrue(forecast.reason.contains("less sleep than your usual"))
    }

    @Test
    fun testEnergyForecastTool_executesAndFormatsOutput() = runBlocking {
        val today = LocalDate.now()
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = today.toString(),
                sleepMinutes = 480
            )
        )

        val tool = EnergyForecastTool(engine)
        val output = tool.execute(emptyMap())

        assertTrue(output.contains("Energy Forecast for Today"))
        assertTrue(output.contains("Crash Risk:"))
        assertTrue(output.contains("Optimal Focus Window:"))
    }

    @Test
    fun testPredictProductivity_linearRegression() {
        val baseline = (1..10).map { i ->
            DailySignalsEntity(
                date = "2025-01-%02d".format(i),
                sleepMinutes = 300 + i * 20, // 320 to 500 mins
                tasksCompleted = 2 + i        // 3 to 12 tasks
            )
        }

        // More sleep -> more tasks completed
        val predictedTasks8Hours = engine.predictProductivity(480, baseline)
        assertTrue(predictedTasks8Hours > 5.0)
    }
}
