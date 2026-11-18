package com.health.friday.tools

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.domain.SignalsBuilder
import com.health.friday.stats.StatEngine
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class GetWeekSummaryTool(
    private val signalsDao: DailySignalsDao,
    private val signalsBuilder: SignalsBuilder
) : AiTool {

    override val name = "get_week_summary"

    override val description =
        "Returns a full summary of the user's last 7 days: averages, totals, and trends for sleep, " +
                "steps, calories, protein, water, mood, screen time, tasks, and workout. " +
                "Use this for 'how was my week', 'weekly review', or broad multi-metric health & life questions."

    override val parameters = listOf(
        AiToolParameter(
            name = "weeks_ago",
            description = "0 = this week (default), 1 = last week, 2 = two weeks ago.",
            required = false
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val weeksAgo = arguments["weeks_ago"]?.trim()?.toIntOrNull()?.coerceIn(0, 8) ?: 0

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val end = today.minusDays((weeksAgo * 7).toLong())
        val start = end.minusDays(6)

        signalsBuilder.buildRange(start, end)

        val signals = signalsDao.getSignalsInRange(start.toString(), end.toString())

        if (signals.isEmpty()) {
            return "No data recorded between $start and $end."
        }

        val n = signals.size
        val weekLabel = if (weeksAgo == 0) "This week" else "$weeksAgo week(s) ago"

        fun avg(vals: List<Double>) = if (vals.isEmpty()) null else vals.average()

        fun trend(vals: List<Double>, threshold: Double = 0.5): String? {
            if (vals.size < 3) return null
            val x = List(vals.size) { it.toDouble() }
            val slope = StatEngine.linearRegression(x, vals)?.first ?: return null
            return when {
                slope > threshold -> "↑"
                slope < -threshold -> "↓"
                else -> "→"
            }
        }

        fun fmtMins(mins: Double): String {
            val total = mins.toInt()
            val h = total / 60
            val m = total % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

        val sleepVals = signals.mapNotNull { it.sleepMinutes?.toDouble() }
        val stepVals = signals.mapNotNull { it.steps?.toDouble() }
        val kcalVals = signals.mapNotNull { it.kcal?.toDouble() }
        val proteinVals = signals.mapNotNull { it.proteinGrams?.toDouble() }
        val waterVals = signals.mapNotNull { it.waterMl?.toDouble() }
        val moodVals = signals.mapNotNull { it.moodScore?.toDouble() }
        val screenVals = signals.mapNotNull { it.screenTimeMinutes?.toDouble() }
        val taskVals = signals.mapNotNull { it.tasksCompleted?.toDouble() }
        val workoutVals = signals.mapNotNull { it.workoutMinutes?.toDouble() }

        return buildString {
            appendLine("$weekLabel ($start to $end, $n days with data):")
            appendLine()

            // Sleep
            avg(sleepVals)?.let {
                append("• Sleep: avg ${fmtMins(it)}")
                trend(sleepVals, 0.5)?.let { t -> append(" $t") }
                appendLine(" — ${sleepVals.size}/$n days recorded")
            } ?: appendLine("• Sleep: not recorded")

            // Steps
            avg(stepVals)?.let {
                append("• Steps: avg ${String.format(Locale.US, "%,d", it.toLong())}/day")
                trend(stepVals, 10.0)?.let { t -> append(" $t") }
                val total = stepVals.sum().toLong()
                appendLine(" — total ${String.format(Locale.US, "%,d", total)}")
            } ?: appendLine("• Steps: not recorded")

            // Calories
            avg(kcalVals)?.let {
                append("• Calories: avg ${it.toInt()} kcal/day")
                trend(kcalVals, 5.0)?.let { t -> append(" $t") }
                appendLine(" — ${kcalVals.size}/$n days logged")
            } ?: appendLine("• Calories: not logged")

            // Protein
            avg(proteinVals)?.let {
                appendLine("• Protein: avg ${String.format(Locale.US, "%.1f", it)} g/day")
            }

            // Water
            avg(waterVals)?.let {
                append("• Water: avg ${it.toInt()} ml/day")
                trend(waterVals, 10.0)?.let { t -> append(" $t") }
                appendLine()
            } ?: appendLine("• Water: not logged")

            // Mood
            avg(moodVals)?.let {
                append("• Mood: avg ${String.format(Locale.US, "%.1f", it)}/5")
                trend(moodVals, 0.05)?.let { t -> append(" $t") }
                appendLine(" — ${moodVals.size}/$n days logged")
            } ?: appendLine("• Mood: not logged")

            // Screen time
            avg(screenVals)?.let {
                append("• Screen time: avg ${fmtMins(it)}/day")
                trend(screenVals, 0.5)?.let { t -> append(" $t") }
                appendLine()
            } ?: appendLine("• Screen time: not recorded")

            // Tasks
            avg(taskVals)?.let {
                append("• Tasks completed: avg ${String.format(Locale.US, "%.1f", it)}/day")
                trend(taskVals, 0.1)?.let { t -> append(" $t") }
                val total = taskVals.sum().toInt()
                appendLine(" — $total total this week")
            } ?: appendLine("• Tasks: not recorded")

            // Workout
            avg(workoutVals)?.let {
                val activeDays = workoutVals.count { v -> v > 0 }
                appendLine("• Workout: $activeDays active days, avg ${fmtMins(it)}/day on active days")
            } ?: appendLine("• Workout: none recorded")
        }.trimEnd()
    }
}
