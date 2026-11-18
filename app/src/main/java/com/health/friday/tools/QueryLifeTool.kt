package com.health.friday.tools

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.domain.SignalsBuilder
import com.health.friday.util.DateParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class QueryLifeTool(
    private val signalsDao: DailySignalsDao,
    private val signalsBuilder: SignalsBuilder
) : AiTool {

    override val name = "query_life"

    override val description =
        "Average, minimum, maximum and total of one life metric over a date range. " +
                "Metrics: sleep, steps, calories, protein, carbs, fat, water, screen_time, " +
                "tasks, workout, mood. Only days that actually have data are counted. " +
                "Ranges are limited to 90 days."

    override val parameters = listOf(
        AiToolParameter("metric", "One of: sleep, steps, calories, protein, carbs, fat, water, screen_time, tasks, workout, mood.", required = true),
        AiToolParameter("startDate", "Start day: YYYY-MM-DD, yesterday, or '7 days ago'.", required = true),
        AiToolParameter("endDate", "End day (defaults to today).", required = false)
    )

    private enum class MetricUnit { MINUTES, STEPS, KCAL, GRAMS, ML, COUNT, SCORE }

    private class Metric(
        val label: String,
        val unit: MetricUnit,
        val sumIsMeaningful: Boolean,
        val pick: (DailySignalsEntity) -> Double?
    )

    private fun metricFor(raw: String): Metric? {
        return when (raw.trim().lowercase().replace(' ', '_').replace('-', '_')) {
            "sleep", "sleep_minutes" ->
                Metric("Sleep", MetricUnit.MINUTES, false) { it.sleepMinutes?.toDouble() }
            "steps" ->
                Metric("Steps", MetricUnit.STEPS, true) { it.steps?.toDouble() }
            "kcal", "calories", "calorie" ->
                Metric("Calories", MetricUnit.KCAL, true) { it.kcal?.toDouble() }
            "protein" ->
                Metric("Protein", MetricUnit.GRAMS, true) { it.proteinGrams?.toDouble() }
            "carbs", "carbohydrates" ->
                Metric("Carbs", MetricUnit.GRAMS, true) { it.carbsGrams?.toDouble() }
            "fat" ->
                Metric("Fat", MetricUnit.GRAMS, true) { it.fatGrams?.toDouble() }
            "water", "hydration" ->
                Metric("Water", MetricUnit.ML, true) { it.waterMl?.toDouble() }
            "screen_time", "screen", "screentime" ->
                Metric("Screen time", MetricUnit.MINUTES, true) { it.screenTimeMinutes?.toDouble() }
            "tasks", "tasks_completed" ->
                Metric("Tasks completed", MetricUnit.COUNT, true) { it.tasksCompleted.toDouble() }
            "workout" ->
                Metric("Workout", MetricUnit.MINUTES, true) { it.workoutMinutes.toDouble() }
            "mood" ->
                Metric("Mood", MetricUnit.SCORE, false) { it.moodScore?.toDouble() }
            else -> null
        }
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val metricName = arguments["metric"].orEmpty()
        val metric = metricFor(metricName)
            ?: return "Unknown metric '$metricName'. Use: sleep, steps, calories, protein, " +
                    "carbs, fat, water, screen_time, tasks, workout, mood."

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        var start = parseDate(arguments["startDate"].orEmpty(), zone)
            ?: return "I couldn't read the start date '${arguments["startDate"].orEmpty()}'."

        var end = parseDate(arguments["endDate"].orEmpty(), zone) ?: today

        if (end.isAfter(today)) end = today
        if (start.isAfter(today)) return "The start date is in the future."
        if (start.isAfter(end)) {
            val swapped = start
            start = end
            end = swapped
        }

        var note = ""
        if (start.isBefore(end.minusDays(MAX_DAYS - 1L))) {
            start = end.minusDays(MAX_DAYS - 1L)
            note = " (range limited to the last $MAX_DAYS days)"
        }

        // Make sure every day in the range has been compiled.
        signalsBuilder.buildRange(start, end)

        val signals = signalsDao.getSignalsInRange(start.toString(), end.toString())

        val points = signals.mapNotNull { signal ->
            metric.pick(signal)?.let { value -> signal.date to value }
        }

        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1

        if (points.isEmpty()) {
            return "No ${metric.label.lowercase(Locale.ENGLISH)} data recorded from $start to $end$note."
        }

        val average = points.map { it.second }.average()
        val min = points.minByOrNull { it.second }!!
        val max = points.maxByOrNull { it.second }!!

        val sb = StringBuilder()
        sb.append("${metric.label}, $start to $end$note: ")
        sb.append("data on ${points.size} of $totalDays days. ")
        sb.append("Average ${format(metric.unit, average)}, ")
        sb.append("min ${format(metric.unit, min.second)} (${min.first}), ")
        sb.append("max ${format(metric.unit, max.second)} (${max.first}).")

        if (metric.sumIsMeaningful) {
            sb.append(" Total ${format(metric.unit, points.sumOf { it.second })}.")
        }

        return sb.toString()
    }

    private fun parseDate(text: String, zone: ZoneId): LocalDate? {
        val trimmed = text.trim()

        return try {
            LocalDate.parse(trimmed)
        } catch (_: Exception) {
            DateParser.parseDayStart(trimmed)?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }
        }
    }

    private fun format(unit: MetricUnit, value: Double): String =
        when (unit) {
            MetricUnit.MINUTES -> {
                val total = value.toInt()
                val hours = total / 60
                val rest = total % 60
                if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
            }
            MetricUnit.STEPS -> String.format(Locale.US, "%,d", value.toLong())
            MetricUnit.KCAL -> "${String.format(Locale.US, "%,d", value.toLong())} kcal"
            MetricUnit.GRAMS -> "${String.format(Locale.US, "%.1f", value)} g"
            MetricUnit.ML -> "${String.format(Locale.US, "%,d", value.toLong())} ml"
            MetricUnit.COUNT -> String.format(Locale.US, "%.1f", value)
            MetricUnit.SCORE -> "${String.format(Locale.US, "%.1f", value)}/5"
        }

    private companion object {
        const val MAX_DAYS = 90
    }
}
