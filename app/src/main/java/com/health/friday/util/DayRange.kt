package com.health.friday.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar

object DayRange {

    // Returns (start of today, start of tomorrow) in milliseconds.
    fun today(): Pair<Long, Long> {

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val start = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)

        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    // Moves a start-of-day time by whole days (negative = earlier).
    fun shiftDays(dayStart: Long, days: Int): Long {

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStart
        calendar.add(Calendar.DAY_OF_YEAR, days)

        return calendar.timeInMillis
    }

    // The last `count` days including today, oldest first.
    fun lastDays(count: Int): List<Pair<Long, Long>> {

        val todayStart = today().first

        val ranges = mutableListOf<Pair<Long, Long>>()

        for (offset in (count - 1) downTo 0) {

            val start = shiftDays(todayStart, -offset)
            val end = shiftDays(start, 1)

            ranges.add(Pair(start, end))
        }

        return ranges
    }

    // Emits today's range now, then again every time midnight passes.
    fun todayFlow(): Flow<Pair<Long, Long>> = flow {

        while (true) {

            val range = today()

            emit(range)

            val wait = range.second - System.currentTimeMillis()

            delay(if (wait > 0) wait else 1000L)
        }
    }
}