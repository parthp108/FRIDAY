package com.health.friday.mood

import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogMoodToolTest {

    @Test
    fun testLogMoodQualitativeParsingAndExecution() = runBlocking {
        val fakeDao = FakeDailySignalsDao()
        // Pre-populate date
        fakeDao.insertOrUpdate(DailySignalsEntity(date = "2026-11-05"))

        val tool = LogMoodTool(fakeDao)

        // Test qualitative word "amazing" -> score 5
        val result = tool.execute(mapOf("score" to "amazing", "date" to "2026-11-05"))
        assertTrue("Expected 5/5 in result, got: $result", result.contains("5/5 (great)"))

        val updatedSignal = fakeDao.getSignalForDate("2026-11-05")
        assertEquals(5, updatedSignal?.moodScore)

        // Test qualitative word "bad" -> score 2
        val badResult = tool.execute(mapOf("score" to "bad", "date" to "2026-11-05"))
        assertTrue("Expected 2/5 in result, got: $badResult", badResult.contains("2/5 (bad)"))

        // Test invalid numeric score outside 1..5 range
        val invalidResult = tool.execute(mapOf("score" to "10"))
        assertTrue(invalidResult.contains("Nothing logged"))
    }
}
