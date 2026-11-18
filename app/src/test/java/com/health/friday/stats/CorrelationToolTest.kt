package com.health.friday.stats

import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CorrelationToolTest {

    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var tool: CorrelationTool

    @Before
    fun setUp() {
        fakeSignalsDao = FakeDailySignalsDao()
        tool = CorrelationTool(fakeSignalsDao)
    }

    @Test
    fun testCorrelationTool_lessThan7Days_refusesToGuess() = runBlocking {
        // Only 3 days of data in database
        val today = LocalDate.now()
        for (i in 0 until 3) {
            val dateStr = today.minusDays(i.toLong()).toString()
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = dateStr,
                    steps = 5000 + i * 1000,
                    sleepMinutes = 400 + i * 20
                )
            )
        }

        val result = tool.execute(mapOf("metric1" to "steps", "metric2" to "sleep"))

        // Must refuse to calculate with fewer than 7 days
        assertTrue(result.contains("Not enough data") || result.contains("need at least 7"))
    }

    @Test
    fun testCorrelationTool_sufficientData_calculatesCorrelation() = runBlocking {
        // 10 days of data in database
        val today = LocalDate.now()
        for (i in 0 until 10) {
            val dateStr = today.minusDays(i.toLong()).toString()
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = dateStr,
                    steps = 5000 + i * 1000,
                    sleepMinutes = 350 + i * 30
                )
            )
        }

        val result = tool.execute(mapOf("metric1" to "steps", "metric2" to "sleep"))

        // Should return calculated Pearson r and Spearman rho
        assertTrue(result.contains("Analyzed 10 days"))
        assertTrue(result.contains("Pearson r"))
        assertTrue(result.contains("Spearman rho"))
    }

    @Test
    fun testCorrelationTool_sameMetric_returnsError() = runBlocking {
        val result = tool.execute(mapOf("metric1" to "steps", "metric2" to "steps"))
        assertTrue(result.contains("Please provide two different metrics"))
    }
}
