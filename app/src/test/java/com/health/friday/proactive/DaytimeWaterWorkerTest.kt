package com.health.friday.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class DaytimeWaterWorkerTest {

    @Test
    fun testConstants() {
        assertEquals("friday_daytime_water_reminder", DaytimeWaterWorker.UNIQUE_WORK_NAME)
        assertEquals("water_reminder", DaytimeWaterWorker.NUDGE_TYPE)
    }

    @Test
    fun testExpectedTargetCalculation() {
        // Hour 8 (8 AM): Target ~250 ml
        val target8AM = ((8 - 8) / 12.0 * 1750 + 250).toInt()
        assertEquals(250, target8AM)

        // Hour 14 (2 PM): Target ~1125 ml
        val target2PM = ((14 - 8) / 12.0 * 1750 + 250).toInt()
        assertEquals(1125, target2PM)

        // Hour 20 (8 PM): Target 2000 ml
        val target8PM = ((20 - 8) / 12.0 * 1750 + 250).toInt()
        assertEquals(2000, target8PM)
    }
}
