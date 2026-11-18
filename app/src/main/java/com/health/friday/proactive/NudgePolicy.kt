package com.health.friday.proactive

import com.health.friday.util.DayRange
import java.util.Calendar

class NudgePolicy(private val dao: ProactiveDao) {

    suspend fun canNudge(type: String): Boolean {
        val isWaterReminder = type == WATER_REMINDER_TYPE || type.startsWith("water_")

        // 1. Daytime Window & Quiet Hours
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (isWaterReminder) {
            // Daytime water reminders run between 8:00 AM and 8:00 PM (hours 8..19)
            if (hour !in 8..19) return false
        } else {
            // Quiet Hours for general nudges: 00:00 to 07:00
            if (hour in 0..6) return false
        }

        val todayStart = DayRange.today().first

        // 2. Hard Cap
        if (isWaterReminder) {
            // Water reminders have a budget of up to 6 per day
            val waterNudgesToday = dao.getNudgesSince(todayStart)
                .count { it.type == WATER_REMINDER_TYPE }
            if (waterNudgesToday >= 6) return false
        } else {
            // General nudges: Max 3 autonomous nudges per day
            val nudgesToday = dao.getNudgesSince(todayStart)
                .count { !it.type.startsWith(PROTOCOL_PREFIX) && it.type != WATER_REMINDER_TYPE }
            if (nudgesToday >= 3) return false
        }

        // 3. Cooldown: 2 hours for water reminders, 4 hours for other nudges
        val cooldownMillis = if (isWaterReminder) 2 * 3600 * 1000L else 4 * 3600 * 1000L
        val lastNudge = dao.getLastNudgeOfType(type)
        val now = System.currentTimeMillis()
        if (lastNudge != null && (now - lastNudge.timestamp) < cooldownMillis) {
            return false
        }

        // 4. Anti-Annoyance Backoff
        // If the last 3 nudges of this type were ignored (interacted = false), block for 72 hours
        val recentNudges = dao.getRecentNudgesOfType(type, 3)
        if (lastNudge != null && recentNudges.size == 3 && recentNudges.all { !it.interacted }) {
            if (now - lastNudge.timestamp < 72 * 3600 * 1000L) {
                return false
            }
        }

        return true
    }

    /** Adaptive Water Nudge Cooldown Check */
    suspend fun canNudgeWater(currentWaterMl: Int, expectedTargetMl: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 8..21) return false

        val todayStart = DayRange.today().first
        val waterNudgesToday = dao.getNudgesSince(todayStart).count { it.type == WATER_REMINDER_TYPE }

        // Hard Cap: Max 4 water nudges per day
        if (waterNudgesToday >= 4) return false

        val lastNudge = dao.getLastNudgeOfType(WATER_REMINDER_TYPE) ?: return true
        val now = System.currentTimeMillis()
        val elapsed = now - lastNudge.timestamp

        // Strict Minimum Cooldown: 60 minutes if behind pace, 2 hours if well hydrated
        val requiredCooldownMs = if (currentWaterMl >= 700 || currentWaterMl >= expectedTargetMl) {
            120 * 60 * 1000L // 2 hours
        } else {
            60 * 60 * 1000L // 60 minutes
        }

        return elapsed >= requiredCooldownMs
    }

    /** Records that a nudge was sent. Returns the new nudge id (put it in the notification intents). */
    suspend fun recordNudge(type: String): Long {
        return dao.insertNudge(
            NudgeHistoryEntity(
                type = type,
                timestamp = System.currentTimeMillis(),
                interacted = false
            )
        )
    }

    /** Marks one specific nudge as interacted. */
    suspend fun markInteracted(nudgeId: Long) {
        val nudge = dao.getNudgeById(nudgeId) ?: return
        if (!nudge.interacted) {
            dao.updateNudge(nudge.copy(interacted = true))
        }
    }

    /** Fallback when only the type is known: marks the most recent nudge of that type. */
    suspend fun markInteracted(type: String) {
        val last = dao.getLastNudgeOfType(type)
        if (last != null && !last.interacted) {
            dao.updateNudge(last.copy(interacted = true))
        }
    }

    companion object {
        const val PROTOCOL_PREFIX = "protocol:"
        const val WATER_REMINDER_TYPE = "water_reminder"
    }
}
