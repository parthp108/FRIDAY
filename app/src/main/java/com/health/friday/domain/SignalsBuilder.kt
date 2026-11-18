package com.health.friday.domain

import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.data.local.MealDao
import com.health.friday.data.local.TodoDao
import com.health.friday.data.local.WaterDao
import com.health.friday.device.DeviceUsageRepository
import com.health.friday.device.HealthConnectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * Compiles every data source into one daily_signals row per day.
 *
 * What fills which column:
 *  - meals        -> kcal, protein, carbs, fat, lastMealHour (null when no meals)
 *  - water        -> waterMl (null when nothing logged)
 *  - todos        -> tasksCompleted, tasksPending
 *  - Health Connect (last 30 days only, that is all it keeps) -> steps, sleep
 *  - Usage stats  (today only, the OS API we use is today-only) -> screenTimeMinutes
 *
 * NOT filled by anything yet (stay null / 0): restingHr, moodScore, workoutMinutes.
 *
 * Unknown stays null. A day with no meals is "unknown", never "0 kcal".
 */
class SignalsBuilder(
    private val signalsDao: DailySignalsDao,
    private val mealDao: MealDao,
    private val waterDao: WaterDao,
    private val todoDao: TodoDao,
    private val healthConnectRepo: HealthConnectRepository,
    private val usageRepo: DeviceUsageRepository
) {

    // Builds read-modify-write a row, so they must not overlap.
    private val mutex = Mutex()

    suspend fun buildSignalsForDate(epochMillis: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val zone = ZoneId.systemDefault()
                val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

                buildDay(date, zone, hasHealthAccess())
            }
        }

    /** Builds every day from startDate to endDate inclusive. */
    suspend fun buildRange(startDate: LocalDate, endDate: LocalDate) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val zone = ZoneId.systemDefault()
                val healthAccess = hasHealthAccess()

                var date = startDate
                while (!date.isAfter(endDate)) {
                    buildDay(date, zone, healthAccess)
                    date = date.plusDays(1)
                }
            }
        }

    /** Today and the days before it, oldest last. Keeps finished days up to date. */
    suspend fun buildRecentDays(days: Int = 3) {
        val today = LocalDate.now()
        buildRange(today.minusDays((days - 1).toLong()), today)
    }

    private suspend fun hasHealthAccess(): Boolean =
        try {
            healthConnectRepo.hasAccess()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private suspend fun buildDay(
        date: LocalDate,
        zone: ZoneId,
        healthAccess: Boolean
    ) {
        val dateStr = date.toString() // ISO yyyy-MM-dd, always ASCII digits
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val today = LocalDate.now(zone)
        val isToday = date == today

        // 1. Meals
        val meals = mealDao.getMealsForDay(start, endExclusive).first()
        val hasMeals = meals.isNotEmpty()

        var lastMealHour: Float? = null

        if (hasMeals) {
            val last = meals.maxByOrNull { it.timestamp }

            if (last != null) {
                val time = Instant.ofEpochMilli(last.timestamp).atZone(zone).toLocalTime()
                lastMealHour = time.hour + time.minute / 60f
            }
        }

        // 2. Water
        val waterEntries = waterDao.getEntriesForDay(start, endExclusive).first()

        // 3. Tasks
        val allTodos = todoDao.getAll().first()

        var tasksCompleted = 0
        var tasksPending = 0

        for (todo in allTodos) {
            val completedAt = todo.completedAt
            val dueAt = todo.dueAt

            val completedToday =
                completedAt != null && completedAt >= start && completedAt < endExclusive
            val dueToday =
                dueAt != null && dueAt >= start && dueAt < endExclusive
            val createdToday =
                todo.createdAt >= start && todo.createdAt < endExclusive

            if (todo.isDone) {
                if (completedToday) tasksCompleted++
            } else if (dueToday || (dueAt == null && createdToday)) {
                tasksPending++
            }
        }

        // 4. Previously recorded signals for this day
        val existing = signalsDao.getSignalForDate(dateStr)

        var steps = existing?.steps
        var sleepMinutes = existing?.sleepMinutes
        var sleepStart = existing?.sleepStartEpoch
        var sleepEnd = existing?.sleepEndEpoch
        var screenTime = existing?.screenTimeMinutes
        var restingHr = existing?.restingHr
        var workoutMinutes = existing?.workoutMinutes ?: 0
        var activeCaloriesKcal = existing?.activeCaloriesKcal
        var distanceMeters = existing?.distanceMeters
        var hrvMs = existing?.hrvMs
        var spo2Percent = existing?.spo2Percent

        // 5. Query full Health Connect telemetry
        val inHealthWindow = !date.isBefore(today.minusDays(29))

        if (healthAccess && inHealthWindow) {
            try {
                val summary = healthConnectRepo.getTodayHealth()

                if (summary.steps > 0) steps = summary.steps.toInt()
                if (summary.sleepMinutes > 0) sleepMinutes = summary.sleepMinutes.toInt()
                if (summary.heartRate != null) {
                    restingHr = summary.heartRate.toInt()
                } else if (summary.restingHeartRate != null) {
                    restingHr = summary.restingHeartRate.toInt()
                }

                if (summary.activeTimeMinutes > 0) workoutMinutes = summary.activeTimeMinutes.toInt()
                if (summary.activeCaloriesKcal != null && summary.activeCaloriesKcal > 0) activeCaloriesKcal = summary.activeCaloriesKcal
                if (summary.distanceMeters != null && summary.distanceMeters > 0.0) distanceMeters = summary.distanceMeters
                if (summary.hrvRmssdMs != null) hrvMs = summary.hrvRmssdMs
                if (summary.oxygenSaturationPercentage != null) spo2Percent = summary.oxygenSaturationPercentage
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep whatever was recorded before.
            }
        }

        // 6. Screen time: the usage API we use only reports today.
        if (isToday) {
            try {
                if (usageRepo.hasAccess()) {
                    val minutes = (usageRepo.getTodayUsage().totalMillis / 60_000L).toInt()

                    if (minutes > 0) {
                        screenTime = minutes
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep whatever was recorded before.
            }
        }

        signalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = dateStr,
                sleepMinutes = sleepMinutes,
                sleepStartEpoch = sleepStart,
                sleepEndEpoch = sleepEnd,
                steps = steps,
                restingHr = restingHr,
                kcal = if (hasMeals) meals.sumOf { it.calories } else null,
                proteinGrams = if (hasMeals) meals.sumOf { it.protein }.toFloat() else null,
                carbsGrams = if (hasMeals) meals.sumOf { it.carbohydrates }.toFloat() else null,
                fatGrams = if (hasMeals) meals.sumOf { it.fat }.toFloat() else null,
                waterMl = if (waterEntries.isNotEmpty()) waterEntries.sumOf { it.amountMl } else null,
                lastMealHour = lastMealHour,
                screenTimeMinutes = screenTime,
                tasksCompleted = tasksCompleted,
                tasksPending = tasksPending,
                moodScore = existing?.moodScore,
                workoutMinutes = workoutMinutes,
                activeCaloriesKcal = activeCaloriesKcal,
                distanceMeters = distanceMeters,
                hrvMs = hrvMs,
                spo2Percent = spo2Percent
            )
        )
    }
}
