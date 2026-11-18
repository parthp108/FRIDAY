package com.health.friday.proactive

import com.health.friday.util.DayRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class NudgePolicyTest {

    private lateinit var fakeDao: FakeProactiveDao
    private lateinit var nudgePolicy: NudgePolicy

    @Before
    fun setUp() {
        fakeDao = FakeProactiveDao()
        nudgePolicy = NudgePolicy(fakeDao)
    }

    @Test
    fun testRecordNudgeAndMarkInteracted() = runBlocking {
        val nudgeType = "evening_checkin"
        val id = nudgePolicy.recordNudge(nudgeType)

        val inserted = fakeDao.getNudgeById(id)
        assertTrue(inserted != null)
        assertEquals(nudgeType, inserted?.type)
        assertFalse(inserted!!.interacted)

        // Mark interacted by ID
        nudgePolicy.markInteracted(id)
        val updated = fakeDao.getNudgeById(id)
        assertTrue(updated!!.interacted)
    }

    @Test
    fun testMarkInteractedByType() = runBlocking {
        val nudgeType = "evening_checkin"
        nudgePolicy.recordNudge(nudgeType)

        val lastNudgeBefore = fakeDao.getLastNudgeOfType(nudgeType)
        assertFalse(lastNudgeBefore!!.interacted)

        // Mark interacted by type
        nudgePolicy.markInteracted(nudgeType)
        val lastNudgeAfter = fakeDao.getLastNudgeOfType(nudgeType)
        assertTrue(lastNudgeAfter!!.interacted)
    }

    @Test
    fun testCanNudgeCooldown_blocksRecentNudgeOfSameType() = runBlocking {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 0..6) {
            // Quiet hours automatically block
            assertFalse(nudgePolicy.canNudge("evening_checkin"))
            return@runBlocking
        }

        val nudgeType = "evening_checkin"
        // Record a nudge right now
        fakeDao.insertNudge(
            NudgeHistoryEntity(
                type = nudgeType,
                timestamp = System.currentTimeMillis(),
                interacted = false
            )
        )

        // Cooldown period (4 hours) should block subsequent nudge of same type
        assertFalse(nudgePolicy.canNudge(nudgeType))
    }

    @Test
    fun testCanNudgeHardCap_blocksMoreThan3NudgesInDay() = runBlocking {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 0..6) {
            assertFalse(nudgePolicy.canNudge("evening_checkin"))
            return@runBlocking
        }

        val todayStart = DayRange.today().first
        // Insert 3 nudges today (using todayStart + offset so it's always within today's range)
        fakeDao.insertNudge(NudgeHistoryEntity(type = "type_1", timestamp = todayStart + 1000L, interacted = false))
        fakeDao.insertNudge(NudgeHistoryEntity(type = "type_2", timestamp = todayStart + 2000L, interacted = false))
        fakeDao.insertNudge(NudgeHistoryEntity(type = "type_3", timestamp = todayStart + 3000L, interacted = false))

        // Hard cap reached (3 nudges today), canNudge should return false
        assertFalse(nudgePolicy.canNudge("type_4"))
    }

    @Test
    fun testWaterReminderPolicy_2HourCooldown() = runBlocking {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 8..19) {
            // Outside daytime hours (8 AM - 8 PM), water reminders are blocked
            assertFalse(nudgePolicy.canNudge(NudgePolicy.WATER_REMINDER_TYPE))
            return@runBlocking
        }

        val now = System.currentTimeMillis()
        // Record a water nudge 1 hour ago
        fakeDao.insertNudge(
            NudgeHistoryEntity(
                type = NudgePolicy.WATER_REMINDER_TYPE,
                timestamp = now - 3600 * 1000L,
                interacted = false
            )
        )

        // 2-hour cooldown should block subsequent water nudge within 1 hour
        assertFalse(nudgePolicy.canNudge(NudgePolicy.WATER_REMINDER_TYPE))
    }
}
