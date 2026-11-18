package com.health.friday.proactive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class EveningCheckinWorkerTest {

    @Test
    fun testDelayUntilNextCheckin_before8PM() {
        val now = LocalDateTime.of(2025, 5, 10, 10, 0, 0)
        val delay = EveningCheckinWorker.delayUntilNextCheckinMillis(now)
        // From 10:00 to 20:00 is 10 hours = 36,000,000 ms
        assertEquals(10 * 3600 * 1000L, delay)
    }

    @Test
    fun testDelayUntilNextCheckin_at8PMExactly() {
        val now = LocalDateTime.of(2025, 5, 10, 20, 0, 0)
        val delay = EveningCheckinWorker.delayUntilNextCheckinMillis(now)
        // Exactly 8 PM -> schedules for 8 PM next day = 24 hours = 86,400,000 ms
        assertEquals(24 * 3600 * 1000L, delay)
    }

    @Test
    fun testDelayUntilNextCheckin_after8PM() {
        val now = LocalDateTime.of(2025, 5, 10, 21, 30, 0)
        val delay = EveningCheckinWorker.delayUntilNextCheckinMillis(now)
        // From 21:30 to 20:00 next day is 22.5 hours = 81,000,000 ms
        val expected = (22 * 3600 + 30 * 60) * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testDelayUntilNextCheckin_midnight() {
        val now = LocalDateTime.of(2025, 5, 10, 0, 0, 0)
        val delay = EveningCheckinWorker.delayUntilNextCheckinMillis(now)
        // From 00:00 to 20:00 is 20 hours = 72,000,000 ms
        assertEquals(20 * 3600 * 1000L, delay)
    }

    @Test
    fun testDelayUntilNextCheckin_afternoon() {
        val now = LocalDateTime.of(2025, 5, 10, 14, 15, 30)
        val delay = EveningCheckinWorker.delayUntilNextCheckinMillis(now)
        // 20:00:00 - 14:15:30 = 5 hours, 44 minutes, 30 seconds
        val expectedSeconds = 5 * 3600 + 44 * 60 + 30
        assertEquals(expectedSeconds * 1000L, delay)
    }
}
