package com.health.friday.predictive

import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.data.local.MealDao
import com.health.friday.util.DayRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PredictiveEngine(
    private val signalsDao: DailySignalsDao,
    private val mealDao: MealDao
) {

    /**
     * Rule-based estimate (not a probability) from two inputs:
     *  - sleep debt: last night vs the user's own 14-day average (fallback 8 h)
     *  - carbs: the largest carb-heavy meal today and when it was eaten; a post-meal dip is
     *    estimated about 2.5 h after it, otherwise the usual early-afternoon dip is used.
     */
    suspend fun forecastEnergy(): EnergyForecast {
        val recent = signalsDao.getRecentSignals(15).sortedByDescending { it.date }
        if (recent.isEmpty()) {
            return EnergyForecast("UNKNOWN", null, "Anytime", "Not enough data to forecast.")
        }

        val todayStr = LocalDate.now().toString()
        val today = recent.first().takeIf { it.date == todayStr }
        val baseline = recent.filter { it.date != todayStr }

        val avgSleep = baseline.mapNotNull { it.sleepMinutes }.takeIf { it.size >= 3 }?.average() ?: 480.0
        val todaysSleep = today?.sleepMinutes?.toDouble() ?: avgSleep
        val sleepDebt = avgSleep - todaysSleep

        val (start, end) = DayRange.today()
        val bigMeal = mealDao.getMealsBetween(start, end)
            .filter { it.carbohydrates >= HIGH_CARB_MEAL_G }
            .maxByOrNull { it.carbohydrates }
        val carbDipMinutes = bigMeal?.let { minutesOfDay(it.timestamp) + 150 }

        val debtScore = when {
            sleepDebt > 90 -> 2
            sleepDebt > 30 -> 1
            else -> 0
        }
        val carbScore = if (bigMeal != null) 1 else 0

        val risk = when (debtScore + carbScore) {
            0 -> "LOW"
            1 -> "MODERATE"
            2 -> "HIGH"
            else -> "SEVERE"
        }

        val crashMinutes = when {
            carbDipMinutes != null -> carbDipMinutes
            debtScore > 0 -> 14 * 60 + 30
            else -> null
        }

        val focusWindow = when {
            debtScore == 2 -> "08:30 - 11:00"
            debtScore == 1 || carbScore == 1 -> "09:00 - 12:00"
            else -> "09:00 - 13:00"
        }

        val parts = mutableListOf<String>()
        if (debtScore > 0) parts.add("${sleepDebt.toInt()} min less sleep than your usual")
        if (bigMeal != null) parts.add("a ${bigMeal.carbohydrates.toInt()} g carb meal (${bigMeal.name}) at ${fmt(minutesOfDay(bigMeal.timestamp))}")
        val reason = if (parts.isEmpty()) "Sleep is near your normal and no heavy carb meal today."
        else "Based on " + parts.joinToString(" and ") + ". This is an estimate, not a measured probability."

        return EnergyForecast(risk, crashMinutes?.let { fmt(it % (24 * 60)) }, focusWindow, reason)
    }

    private fun minutesOfDay(epoch: Long): Int =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

    private fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    // Simple linear regression to predict tasks completed based on sleep
    fun predictProductivity(sleepMins: Int, baseline: List<DailySignalsEntity>): Double {
        val validDays = baseline.filter { it.sleepMinutes != null }
        if (validDays.size < 5) return 0.0

        val x = validDays.map { it.sleepMinutes!!.toDouble() }
        val y = validDays.map { it.tasksCompleted.toDouble() }

        val (slope, intercept) = com.health.friday.stats.StatEngine.linearRegression(x, y)
            ?: return y.average()

        val prediction = slope * sleepMins + intercept
        return prediction.coerceAtLeast(0.0) // Cannot predict negative tasks
    }

    private companion object {
        const val HIGH_CARB_MEAL_G = 60.0
    }
}
