package com.health.friday.predictive.solver

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleRiskTool(
    private val calendarReader: CalendarHorizonReader,
    private val solver: SpatioTemporalSolver
) : AiTool {

    override val name = "get_schedule_risks"

    override val description =
        "Reads the user's calendar for the next few hours and reports upcoming events, " +
                "tight transitions, double-bookings, and any predicted overrun/travel clash. " +
                "Use for 'will I be late', 'am I double booked', 'what do my next hours look like'. " +
                "Read-only: never changes the calendar."

    override val parameters = listOf(
        AiToolParameter("hours", "How many hours ahead to look, 1-12. Default 6.", required = false)
    )

    override suspend fun execute(arguments: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            if (!calendarReader.hasPermission()) {
                return@withContext "I can't read your calendar: Calendar permission isn't granted. " +
                        "Enable it in Settings > Apps > FRIDAY > Permissions."
            }

            val hours = arguments["hours"]?.trim()?.toIntOrNull()?.coerceIn(1, 12) ?: 6
            val now = System.currentTimeMillis()
            val events = calendarReader.queryUpcomingHorizon(now, hours)
                .filter { it.endEpochMs > now }

            if (events.isEmpty()) {
                return@withContext "No timed events on your calendar in the next $hours hours."
            }

            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            fun t(ms: Long) = fmt.format(Date(ms))

            buildString {
                appendLine("Next $hours hours: ${events.size} event(s).")
                events.take(10).forEach { e ->
                    append("- ${t(e.startEpochMs)}-${t(e.endEpochMs)} ${e.title}")
                    if (!e.locationLabel.isNullOrBlank()) append(" @ ${e.locationLabel}")
                    appendLine()
                }

                val overlaps = solver.findScheduledOverlaps(events)
                if (overlaps.isNotEmpty()) {
                    appendLine()
                    appendLine("Double-booked on your calendar:")
                    overlaps.take(5).forEach { (a, b) ->
                        appendLine("- '${a.title}' overlaps '${b.title}'")
                    }
                }

                val tight = solver.adjacentPairs(now, events).filter { it.gapMinutes < 15.0 }
                if (tight.isNotEmpty()) {
                    appendLine()
                    appendLine("Tight transitions (from your calendar, not a prediction):")
                    tight.take(5).forEach { p ->
                        appendLine("- '${p.lead.title}' -> '${p.next.title}': ${p.gapMinutes.toInt()} min gap")
                    }
                }

                val clashes = solver.detectHorizonClashes(now, null, null, events)
                appendLine()
                if (clashes.isEmpty()) {
                    append(
                        "No overrun or travel clash predicted. Predictions need either learned " +
                                "overrun history for a meeting type or map coordinates in the event " +
                                "location; without those I only report the calendar facts above."
                    )
                } else {
                    appendLine("Predicted clashes:")
                    clashes.forEach { c ->
                        appendLine(
                            "- '${c.currentOrLeadingEvent.title}' may run into " +
                                    "'${c.conflictingNextEvent.title}' " +
                                    "(risk ${(c.clashProbability * 100).toInt()}%, expected slack " +
                                    "${c.expectedSlackMinutes.toInt()} min). " +
                                    "A safer start for the second would be ${t(c.recommendedNewStartEpochMs)}."
                        )
                    }
                }
            }.trimEnd()
        }
}
