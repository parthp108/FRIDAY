package com.health.friday.tools

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.domain.SignalsBuilder
import com.health.friday.util.DateParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class GetDayTool(
    private val signalsDao: DailySignalsDao,
    private val signalsBuilder: SignalsBuilder
) : AiTool {

    override val name = "get_day"

    override val description =
        "Gets the snapshot of the user's life for one day: sleep, steps, " +
                "meals and macros, water, tasks, screen time. Fields that were not " +
                "recorded are reported as not recorded, which is unknown, not zero."

    override val parameters = listOf(
        AiToolParameter(
            "date",
            "The day: YYYY-MM-DD, today, yesterday, or '3 days ago'.",
            required = true
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val raw = arguments["date"].orEmpty()

        val dayStart = DateParser.parseDayStart(raw)
            ?: return "I couldn't read the date '$raw', or it is in the future. " +
                    "Use YYYY-MM-DD, today, yesterday, or '3 days ago'."

        // Rebuild first so the answer always reflects the current records
        // (including meals added or undone since the last build).
        signalsBuilder.buildSignalsForDate(dayStart)

        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()

        val signal = signalsDao.getSignalForDate(date.toString())
            ?: return "No data recorded for $date."

        val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        val sleepLine =
            if (signal.sleepMinutes != null) {
                val window =
                    if (signal.sleepStartEpoch != null && signal.sleepEndEpoch != null) {
                        val from = Instant.ofEpochMilli(signal.sleepStartEpoch).atZone(zone)
                        val to = Instant.ofEpochMilli(signal.sleepEndEpoch).atZone(zone)
                        " (${from.format(clock)} to ${to.format(clock)})"
                    } else {
                        ""
                    }
                formatMinutes(signal.sleepMinutes) + window
            } else {
                "not recorded"
            }

        val nutritionLine =
            if (signal.kcal != null) {
                "${signal.kcal} kcal " +
                        "(${oneDecimal(signal.proteinGrams)} g protein, " +
                        "${oneDecimal(signal.carbsGrams)} g carbs, " +
                        "${oneDecimal(signal.fatGrams)} g fat)"
            } else {
                "no meals logged"
            }

        return listOf(
            "Life snapshot for $date:",
            "- Sleep: $sleepLine",
            "- Steps: ${signal.steps?.let { String.format(Locale.US, "%,d", it) } ?: "not recorded"}",
            "- Resting HR: ${signal.restingHr?.let { "$it bpm" } ?: "not tracked"}",
            "- Nutrition: $nutritionLine",
            "- Water: ${signal.waterMl?.let { "$it ml" } ?: "none logged"}",
            "- Tasks: ${signal.tasksCompleted} done, ${signal.tasksPending} pending",
            "- Screen time: ${signal.screenTimeMinutes?.let { formatMinutes(it) } ?: "not recorded"}",
            "- Mood: ${signal.moodScore?.let { "$it/5" } ?: "not recorded"}",
            "- Workout: ${if (signal.workoutMinutes > 0) "${signal.workoutMinutes} min" else "none recorded"}"
        ).joinToString("\n")
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60

        return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
    }

    private fun oneDecimal(value: Float?): String =
        String.format(Locale.US, "%.1f", value ?: 0f)
}
