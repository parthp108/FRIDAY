package com.health.friday.proactive

import java.time.LocalTime
import kotlin.math.roundToInt

data class HydrationPacing(
    val isAheadOfPace: Boolean,
    val currentWaterMl: Int,
    val expectedTargetMl: Int,
    val deficitMl: Int,
    val requiredMlPerHour: Int,
    val promptMessage: String?
)

object DynamicHydrationCalculator {

    private const val DAY_START_HOUR = 8  // 8:00 AM
    private const val DAY_END_HOUR = 20   // 8:00 PM (20:00)
    private const val DEFAULT_DAILY_TARGET_ML = 2500

    /**
     * Dynamically calculates hydration pacing as a function of:
     * - Current hour and remaining daylight hours
     * - Past logged water intake vs expected cumulative pace
     * - Required ml/hour needed to comfortably hit goal by 8 PM
     */
    fun calculatePacing(
        currentWaterMl: Int,
        dailyTargetMl: Int = DEFAULT_DAILY_TARGET_ML,
        currentTime: LocalTime = LocalTime.now()
    ): HydrationPacing {

        val currentHour = currentTime.hour

        // Outside daytime hours (before 8 AM or after 8 PM), no nudges required
        if (currentHour < DAY_START_HOUR || currentHour >= DAY_END_HOUR) {
            return HydrationPacing(
                isAheadOfPace = true,
                currentWaterMl = currentWaterMl,
                expectedTargetMl = dailyTargetMl,
                deficitMl = 0,
                requiredMlPerHour = 0,
                promptMessage = null
            )
        }

        val totalDaylightHours = (DAY_END_HOUR - DAY_START_HOUR).toDouble() // 12 hours
        val elapsedHours = (currentHour - DAY_START_HOUR).toDouble() + (currentTime.minute / 60.0)
        val remainingHours = maxOf(1.0, totalDaylightHours - elapsedHours)

        // Dynamic expected target as a function of elapsed daylight hours
        val expectedTargetMl = ((elapsedHours / totalDaylightHours) * dailyTargetMl).roundToInt()

        val deficitMl = expectedTargetMl - currentWaterMl
        val remainingToGoalMl = maxOf(0, dailyTargetMl - currentWaterMl)
        val requiredMlPerHour = (remainingToGoalMl / remainingHours).roundToInt()

        // IF AHEAD OF PACE OR MET GOAL: Suppress notifications!
        if (currentWaterMl >= expectedTargetMl || currentWaterMl >= dailyTargetMl) {
            val surplusMl = currentWaterMl - expectedTargetMl
            return HydrationPacing(
                isAheadOfPace = true,
                currentWaterMl = currentWaterMl,
                expectedTargetMl = expectedTargetMl,
                deficitMl = -surplusMl,
                requiredMlPerHour = requiredMlPerHour,
                promptMessage = null // No reminder needed! User is ahead of pace.
            )
        }

        // IF BEHIND PACE: Generate dynamic personalized message
        val timeLabel = when {
            currentHour < 12 -> "this morning"
            currentHour < 17 -> "this afternoon"
            else -> "this evening"
        }

        val promptMessage = if (currentWaterMl == 0) {
            "Hey boss, you haven't logged any water $timeLabel yet. Have a glass of water to get on track!"
        } else {
            "Hey boss, you're at ${currentWaterMl}ml water $timeLabel—about ${deficitMl}ml behind your target pace. Grab a quick drink!"
        }

        return HydrationPacing(
            isAheadOfPace = false,
            currentWaterMl = currentWaterMl,
            expectedTargetMl = expectedTargetMl,
            deficitMl = deficitMl,
            requiredMlPerHour = requiredMlPerHour,
            promptMessage = promptMessage
        )
    }
}
