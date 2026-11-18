package com.health.friday.stats

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import kotlin.math.abs
import kotlin.math.sqrt

class CorrelationTool(
    private val signalsDao: DailySignalsDao
) : AiTool {
    override val name = "get_correlation"
    override val description =
        "Computes the Pearson correlation (r) and Spearman rank correlation between two of the user's " +
            "daily metrics over the last 60 days. Use it to test claims like 'does eating late hurt my sleep?'. " +
            "Metrics: $METRICS. For effects that land on the NEXT day (late dinner -> that night's sleep, " +
            "which is recorded on the following date), set lag_days=1."
    override val parameters = listOf(
        AiToolParameter("metric1", "First metric (the possible cause).", required = true),
        AiToolParameter("metric2", "Second metric (the possible effect).", required = true),
        AiToolParameter("lag_days", "0 (default) = same day. 1 = metric1 of day D vs metric2 of day D+1.", required = false)
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val m1 = normalize(arguments["metric1"] ?: return "Missing metric1.")
        val m2 = normalize(arguments["metric2"] ?: return "Missing metric2.")
        if (m1 == m2) return "Please provide two different metrics to correlate."

        val lag = arguments["lag_days"]?.trim()?.toIntOrNull()?.coerceIn(0, 3) ?: 0

        if (!SUPPORTED.contains(m1) || !SUPPORTED.contains(m2)) {
            return "Unknown metric. Supported metrics: $METRICS."
        }

        val byDate = signalsDao.getRecentSignals(WINDOW_DAYS).associateBy { it.date }
        if (byDate.size < MIN_DAYS) return "Not enough data (${byDate.size} days). I need at least $MIN_DAYS."

        val x = mutableListOf<Double>()
        val y = mutableListOf<Double>()

        for ((date, row) in byDate) {
            val other = if (lag == 0) row else byDate[shift(date, lag)] ?: continue
            val v1 = extractMetric(row, m1) ?: continue
            val v2 = extractMetric(other, m2) ?: continue
            x.add(v1)
            y.add(v2)
        }

        if (x.size < MIN_DAYS) {
            return "Not enough overlapping days where both $m1 and $m2 were logged (${x.size} found, need $MIN_DAYS)."
        }

        val r = StatEngine.pearson(x, y)
            ?: return "Can't compute a correlation: one of the metrics barely varies across these ${x.size} days."
        val rho = StatEngine.spearman(x, y)

        val slope = StatEngine.linearRegression(x, y)?.first
        // Rough significance: |t| for r with n-2 degrees of freedom; ~2 is the 5% line for n around 30+.
        val n = x.size
        val t = if (abs(r) >= 1.0) Double.POSITIVE_INFINITY else abs(r) * sqrt((n - 2) / (1 - r * r))
        val reliability = when {
            t >= 2.0 -> "statistically distinguishable from chance"
            else -> "could easily be chance with only $n days"
        }

        return buildString {
            append("Analyzed $n days (last $WINDOW_DAYS), $m1 vs $m2")
            if (lag > 0) append(" with $lag-day lag")
            append(".\n")
            append("- Pearson r = ${"%.2f".format(r)} (${StatEngine.interpretCorrelation(r)})\n")
            if (rho != null) append("- Spearman rho = ${"%.2f".format(rho)}\n")
            if (slope != null) append("- Trend: each +1 in $m1 goes with ${"%.3f".format(slope)} change in $m2\n")
            append("- This result is $reliability.\n")
            append("Correlation does not prove causation.")
        }
    }

    private fun normalize(s: String) = s.trim().lowercase().replace(' ', '_').replace('-', '_')

    private fun shift(date: String, days: Int): String =
        java.time.LocalDate.parse(date).plusDays(days.toLong()).toString()

    private fun extractMetric(s: DailySignalsEntity, metric: String): Double? = when (metric) {
        "sleep" -> s.sleepMinutes?.toDouble()
        "steps" -> s.steps?.toDouble()
        "kcal" -> s.kcal?.toDouble()
        "protein" -> s.proteinGrams?.toDouble()
        "carbs" -> s.carbsGrams?.toDouble()
        "fat" -> s.fatGrams?.toDouble()
        "water" -> s.waterMl?.toDouble()
        "last_meal_hour" -> s.lastMealHour?.toDouble()
        "screen_time" -> s.screenTimeMinutes?.toDouble()
        "resting_hr" -> s.restingHr?.toDouble()
        "mood" -> s.moodScore?.toDouble()
        // Days with no recorded tasks/workouts are real zeros only if the day was tracked at all.
        "tasks" -> s.tasksCompleted.toDouble()
        "workout" -> s.workoutMinutes.toDouble()
        else -> null
    }

    private companion object {
        const val WINDOW_DAYS = 60
        const val MIN_DAYS = 7
        val SUPPORTED = setOf(
            "sleep", "steps", "kcal", "protein", "carbs", "fat", "water", "last_meal_hour",
            "screen_time", "resting_hr", "mood", "tasks", "workout"
        )
        const val METRICS =
            "sleep, steps, kcal, protein, carbs, fat, water, last_meal_hour, screen_time, resting_hr, mood, tasks, workout"
    }
}
