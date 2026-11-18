package com.health.friday.predictive

import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PredictProductivityToolTest {

    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var fakeMealDao: FakeMealDaoForPredictive
    private lateinit var predictiveEngine: PredictiveEngine
    private lateinit var tool: PredictProductivityTool

    @Before
    fun setUp() {
        fakeSignalsDao = FakeDailySignalsDao()
        fakeMealDao = FakeMealDaoForPredictive()
        predictiveEngine = PredictiveEngine(fakeSignalsDao, fakeMealDao)
        tool = PredictProductivityTool(predictiveEngine, fakeSignalsDao)
    }

    @Test
    fun testPredictProductivityTool_insufficientData_returnsMessage() = runBlocking {
        val result = tool.execute(emptyMap())
        assertEquals("Not enough historical data to run regression.", result)
    }

    @Test
    fun testPredictProductivityTool_sufficientData_predictsTasks() = runBlocking {
        val today = LocalDate.now()

        // 1. Insert 10 days of historical data
        for (i in 1..10) {
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = today.minusDays(i.toLong()).toString(),
                    sleepMinutes = 300 + i * 20, // 320 to 500 mins
                    tasksCompleted = 2 + i
                )
            )
        }

        // 2. Today's sleep logged: 420 mins (7 hours)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = today.toString(),
                sleepMinutes = 420
            )
        )

        // 3. Execute tool
        val result = tool.execute(emptyMap())

        assertTrue(result.contains("Based on 7h 0m of sleep"))
        assertTrue(result.contains("regression models predict"))
        assertTrue(result.contains("tasks today"))
    }
}
