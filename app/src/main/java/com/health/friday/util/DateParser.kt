package com.health.friday.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateParser {

    private val daysAgo = Regex("^(\\d+)\\s+days?\\s+ago$")

    // Start-of-day time for "today", "yesterday", "day before yesterday",
    // "3 days ago" or "2026-10-01". Empty text means today.
    // Returns null if not understood or in the future.
    fun parseDayStart(text: String): Long? {

        val lower = text.trim().lowercase()

        val todayStart = DayRange.today().first

        val result: Long? =
            when {
                lower.isEmpty() || lower == "today" -> todayStart

                lower == "yesterday" ->
                    DayRange.shiftDays(todayStart, -1)

                lower == "day before yesterday" ->
                    DayRange.shiftDays(todayStart, -2)

                else -> {

                    val ago = daysAgo.find(lower)

                    if (ago != null) {

                        val n = ago.groupValues[1].toIntOrNull()

                        if (n != null && n in 0..365) {
                            DayRange.shiftDays(todayStart, -n)
                        } else {
                            null
                        }

                    } else {
                        parseIso(lower)
                    }
                }
            }

        if (result == null || result > todayStart) {
            return null
        }

        return result
    }

    // A chosen time of day on a given day.
    fun timestampOnDay(dayStart: Long, hour: Int): Long {

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStart
        calendar.set(Calendar.HOUR_OF_DAY, hour)

        return calendar.timeInMillis
    }

    private fun parseIso(text: String): Long? {

        return try {

            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.isLenient = false

            format.parse(text)?.time

        } catch (e: Exception) {
            null
        }
    }
}