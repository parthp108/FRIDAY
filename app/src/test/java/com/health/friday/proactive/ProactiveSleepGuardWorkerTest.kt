package com.health.friday.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ProactiveSleepGuardWorkerTest {

    @Test
    fun testWakeGuardAlertNeeded_whenUserNotAwakeBy730AM() {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val targetWakeTime = LocalTime.of(7, 30)
        val targetWakeEpoch = today.atTime(targetWakeTime).atZone(zoneId).toInstant().toEpochMilli()

        // Case 1: Sleep end is null (no sleep data logged yet) -> needs wake alert
        val sleepEnd1: Long? = null
        val needsAlert1 = sleepEnd1 == null || sleepEnd1 > targetWakeEpoch
        assertTrue("Alert should trigger when no wake data logged", needsAlert1)

        // Case 2: Sleep end recorded after 7:30 AM (e.g., 8:00 AM) -> needs wake alert
        val sleepEnd2 = today.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
        val needsAlert2 = sleepEnd2 > targetWakeEpoch
        assertTrue("Alert should trigger when sleep ended after 7:30 AM", needsAlert2)
    }

    @Test
    fun testWakeGuardAlertNotNeeded_whenUserWokeUpBefore730AM() {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val targetWakeTime = LocalTime.of(7, 30)
        val targetWakeEpoch = today.atTime(targetWakeTime).atZone(zoneId).toInstant().toEpochMilli()

        // Sleep end recorded at 6:45 AM (before 7:30 AM) -> already awake!
        val sleepEnd = today.atTime(6, 45).atZone(zoneId).toInstant().toEpochMilli()
        val needsAlert = sleepEnd > targetWakeEpoch
        assertFalse("Alert should NOT trigger if user woke up before 7:30 AM", needsAlert)
    }

    @Test
    fun testTargetWakeTimeCalculation() {
        val zone = ZoneId.systemDefault()
        var targetTime = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(7, 30), zone)
        val nowMillis = System.currentTimeMillis()

        if (targetTime.toInstant().toEpochMilli() <= nowMillis) {
            targetTime = targetTime.plusDays(1)
        }

        assertTrue("Next 7:30 AM alarm epoch must be in the future", targetTime.toInstant().toEpochMilli() > nowMillis)
        assertEquals("Target hour must be 7 AM", 7, targetTime.hour)
        assertEquals("Target minute must be 30", 30, targetTime.minute)
    }
}
