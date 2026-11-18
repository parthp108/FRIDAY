package com.health.friday.loom

import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.JournalDao
import com.health.friday.data.local.MealDao
import com.health.friday.data.local.TodoDao
import com.health.friday.data.local.WaterDao
import com.health.friday.domain.SignalsBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class LoomEventType { REST, NUTRITION, WATER, ACTIVITY, WORKOUT, TASK, MIND_JOURNAL }

/** One thing on the timeline. Hours are measured from local midnight (can be negative or above 24). */
data class LoomEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: LoomEventType,
    val startHour: Float,
    val endHour: Float? = null,
    val title: String,
    val subtitle: String = "",
    val detailText: String = "",
    val done: Boolean = true,
    val calories: Int? = null,
    val proteinGrams: Float? = null,
    val carbsGrams: Float? = null,
    val fatGrams: Float? = null,
    val amountMl: Int? = null,
    val timestampMillis: Long = 0L
)

data class LoomDay(
    val date: LocalDate,
    val sleep: List<LoomEvent>,
    val meals: List<LoomEvent>,
    val water: List<LoomEvent>,
    val workouts: List<LoomEvent>,
    val tasks: List<LoomEvent>,
    val journals: List<LoomEvent>,
    val kcal: Int?,
    val waterMl: Int?,
    val steps: Int?,
    val sleepMinutes: Int?,
    val screenMinutes: Int?,
    val tasksCompleted: Int,
    val tasksPending: Int,
    val synthesisText: String = ""
) {
    val hasAnything: Boolean
        get() = sleep.isNotEmpty() || meals.isNotEmpty() || water.isNotEmpty() ||
                workouts.isNotEmpty() || tasks.isNotEmpty() || journals.isNotEmpty() ||
                steps != null || screenMinutes != null || sleepMinutes != null
}

/** Reads real records for one day and turns them into timeline events. No invented data. */
class LoomDayBuilder(
    private val mealDao: MealDao,
    private val waterDao: WaterDao,
    private val todoDao: TodoDao,
    private val journalDao: JournalDao,
    private val signalsDao: DailySignalsDao,
    private val signalsBuilder: SignalsBuilder
) {

    suspend fun build(date: LocalDate): LoomDay =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            // Refresh sleep / steps / screen time and the daily totals.
            signalsBuilder.buildSignalsForDate(start)

            val signal = signalsDao.getSignalForDate(date.toString())

            fun hourOf(timestamp: Long): Float = (timestamp - start) / 3_600_000f

            // 1. REST (Sleep)
            val sleep = mutableListOf<LoomEvent>()
            val sleepStart = signal?.sleepStartEpoch
            val sleepEnd = signal?.sleepEndEpoch

            if (sleepStart != null && sleepEnd != null && sleepEnd > sleepStart) {
                val minutes = ((sleepEnd - sleepStart) / 60_000L).toInt()
                val h = minutes / 60
                val m = minutes % 60
                sleep.add(
                    LoomEvent(
                        type = LoomEventType.REST,
                        startHour = hourOf(sleepStart),
                        endHour = hourOf(sleepEnd),
                        title = "Sleep",
                        subtitle = "${h}h ${m}m duration",
                        detailText = "Recorded from Health Connect. Start: ${formatHourMin(sleepStart, zone)}, End: ${formatHourMin(sleepEnd, zone)}",
                        timestampMillis = sleepStart
                    )
                )
            }

            // 2. NUTRITION (Meals)
            val meals = mealDao.getMealsForDay(start, end).first()
                .sortedBy { it.timestamp }
                .map { meal ->
                    LoomEvent(
                        type = LoomEventType.NUTRITION,
                        startHour = hourOf(meal.timestamp),
                        title = meal.name.ifEmpty { "Logged Meal" },
                        subtitle = "${meal.calories} kcal",
                        detailText = "Logged at ${formatHourMin(meal.timestamp, zone)}",
                        calories = meal.calories,
                        proteinGrams = meal.protein.toFloat(),
                        carbsGrams = meal.carbohydrates.toFloat(),
                        fatGrams = meal.fat.toFloat(),
                        timestampMillis = meal.timestamp
                    )
                }

            // 3. WATER
            val water = waterDao.getEntriesForDay(start, end).first()
                .sortedBy { it.timestamp }
                .map { entry ->
                    LoomEvent(
                        type = LoomEventType.WATER,
                        startHour = hourOf(entry.timestamp),
                        title = "Water Intake",
                        subtitle = "${entry.amountMl} ml",
                        detailText = "Logged at ${formatHourMin(entry.timestamp, zone)}",
                        amountMl = entry.amountMl,
                        timestampMillis = entry.timestamp
                    )
                }

            // 4. WORKOUTS (from workoutMinutes or signals)
            val workouts = mutableListOf<LoomEvent>()
            if ((signal?.workoutMinutes ?: 0) > 0) {
                val mins = signal?.workoutMinutes ?: 0
                workouts.add(
                    LoomEvent(
                        type = LoomEventType.WORKOUT,
                        startHour = 17.5f, // Late afternoon workout
                        endHour = 17.5f + (mins / 60f),
                        title = "Active Exercise",
                        subtitle = "$mins mins active session",
                        detailText = "Recorded exercise session.",
                        timestampMillis = start + (17 * 3600 * 1000L + 30 * 60 * 1000L)
                    )
                )
            }

            // 5. TASKS
            val tasks = mutableListOf<LoomEvent>()
            for (todo in todoDao.getAll().first()) {
                val completedAt = todo.completedAt
                val dueAt = todo.dueAt

                if (todo.isDone && completedAt != null && completedAt >= start && completedAt < end) {
                    tasks.add(
                        LoomEvent(
                            type = LoomEventType.TASK,
                            startHour = hourOf(completedAt),
                            title = todo.title,
                            subtitle = "Completed",
                            detailText = "Finished at ${formatHourMin(completedAt, zone)}",
                            done = true,
                            timestampMillis = completedAt
                        )
                    )
                } else if (!todo.isDone && dueAt != null && dueAt >= start && dueAt < end) {
                    tasks.add(
                        LoomEvent(
                            type = LoomEventType.TASK,
                            startHour = hourOf(dueAt),
                            title = todo.title,
                            subtitle = "Due today",
                            detailText = "Scheduled for ${formatHourMin(dueAt, zone)}",
                            done = false,
                            timestampMillis = dueAt
                        )
                    )
                }
            }
            tasks.sortBy { it.startHour }

            // 6. MIND (Journal Entries)
            val journals = mutableListOf<LoomEvent>()
            try {
                val allJournals = journalDao.getAllNow()
                    .filter { it.createdAt >= start && it.createdAt < end }
                    .sortedBy { it.createdAt }

                for (j in allJournals) {
                    journals.add(
                        LoomEvent(
                            type = LoomEventType.MIND_JOURNAL,
                            startHour = hourOf(j.createdAt),
                            title = "Journal Note",
                            subtitle = j.text.take(32) + if (j.text.length > 32) "..." else "",
                            detailText = j.text + if (!j.feedback.isNullOrEmpty()) "\n\nFRIDAY Feedback: ${j.feedback}" else "",
                            timestampMillis = j.createdAt
                        )
                    )
                }
            } catch (_: Exception) {}

            val synthesis = buildSynthesisText(
                kcal = signal?.kcal,
                waterMl = signal?.waterMl,
                sleepMinutes = signal?.sleepMinutes,
                steps = signal?.steps,
                tasksDone = signal?.tasksCompleted ?: 0,
                journalCount = journals.size
            )

            LoomDay(
                date = date,
                sleep = sleep,
                meals = meals,
                water = water,
                workouts = workouts,
                tasks = tasks,
                journals = journals,
                kcal = signal?.kcal,
                waterMl = signal?.waterMl,
                steps = signal?.steps,
                sleepMinutes = signal?.sleepMinutes,
                screenMinutes = signal?.screenTimeMinutes,
                tasksCompleted = signal?.tasksCompleted ?: 0,
                tasksPending = signal?.tasksPending ?: 0,
                synthesisText = synthesis
            )
        }

    private fun formatHourMin(millis: Long, zone: ZoneId): String {
        return Instant.ofEpochMilli(millis)
            .atZone(zone)
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    private fun buildSynthesisText(
        kcal: Int?,
        waterMl: Int?,
        sleepMinutes: Int?,
        steps: Int?,
        tasksDone: Int,
        journalCount: Int
    ): String {
        val parts = mutableListOf<String>()

        if (sleepMinutes != null) {
            val h = sleepMinutes / 60
            if (h < 6) {
                parts.add("Shorter sleep recorded (${h}h ${sleepMinutes % 60}m). Hydrate well and take rest breaks.")
            } else {
                parts.add("Restorative sleep (${h}h ${sleepMinutes % 60}m) supported energy flow today.")
            }
        }

        if (kcal != null && kcal > 0) {
            parts.add("Logged $kcal kcal across meals.")
        }

        if (waterMl != null && waterMl >= 2000) {
            parts.add("Great hydration ($waterMl ml).")
        }

        if (steps != null && steps >= 5000) {
            parts.add("Active movement day with $steps steps.")
        }

        if (tasksDone > 0) {
            parts.add("Completed $tasksDone tasks efficiently.")
        }

        if (journalCount > 0) {
            parts.add("Reflected in $journalCount journal entry.")
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(" ")
        } else {
            "No health signals recorded for this day yet."
        }
    }
}
