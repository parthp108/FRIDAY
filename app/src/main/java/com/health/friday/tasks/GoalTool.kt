package com.health.friday.tasks

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.Goal
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GoalTool(
    private val repository: GoalRepository
) : AiTool {


    override val name = "goal"

    override val description =
        "Manages future actionable goals. Use this only when the user expresses " +
                "actionable intent with a future timeline. Actionable items with no " +
                "timeline or a today timeline are TODOs, not goals. Feelings, wishes, " +
                "and non-actionable statements are neither TODOs nor goals. " +
                "Can list, add, complete, or delete goals."

    override val parameters = listOf(
        AiToolParameter(
            name = "action",
            description =
                "What to do: list, add, complete, or delete.",
            required = true
        ),
        AiToolParameter(
            name = "title",
            description =
                "The actionable goal title. Required for add, complete, and delete."
        ),
        AiToolParameter(
            name = "timeline",
            description =
                "The future timeline expressed by the user, such as tomorrow, " +
                        "next week, next month, this Friday, next Friday, or December. " +
                        "Required when adding a goal."
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val action = arguments["action"]?.trim()?.lowercase().orEmpty()
        val title = arguments["title"]?.trim().orEmpty()
        val timeline = arguments["timeline"]?.trim().orEmpty()

        return when (action) {
            "list" -> listGoals()
            "add" -> addGoal(title, timeline)
            "complete" -> setGoalDone(title, true)
            "delete" -> deleteGoal(title)
            else ->
                "Nothing changed. The goal action must be list, add, complete, or delete."
        }
    }

    private suspend fun listGoals(): String {
        val goals = repository.getGoals().first()

        if (goals.isEmpty()) {
            return "There are no goals."
        }

        return buildString {
            append("Goals:\n")

            for (goal in goals) {
                val status = if (goal.isDone) "completed" else "open"
                val date = goal.targetDate?.let { formatDate(it) }

                if (date != null) {
                    append("• ${goal.title} — $status — $date\n")
                } else {
                    append("• ${goal.title} — $status\n")
                }
            }
        }.trimEnd()
    }

    private suspend fun addGoal(
        title: String,
        timeline: String
    ): String {
        if (title.isBlank()) {
            return "Nothing added. No goal title was given."
        }

        if (timeline.isBlank()) {
            return "Nothing added. A future timeline is required for a goal."
        }

        val now = System.currentTimeMillis()
        val targetDate = parseTimeline(timeline, now)
            ?: return "Nothing added. I couldn't understand the future timeline \"$timeline\"."

        val startOfToday = startOfToday()

        if (targetDate <= startOfToday) {
            return "Nothing added. A goal must have a future timeline. Today should be a TODO."
        }

        val goals = repository.getGoals().first()

        val duplicate = goals.firstOrNull {
            !it.isDone &&
                    it.title.equals(title.trim(), ignoreCase = true)
        }

        if (duplicate != null) {
            return "That goal already exists: \"${duplicate.title}\"."
        }

        val saved = repository.addGoal(
            title = title,
            targetDate = targetDate
        )

        if (saved == null) {
            return "Nothing added. The goal title was blank."
        }

        return "Added goal: \"${saved.title}\" for ${formatDate(targetDate)}."
    }

    private suspend fun setGoalDone(
        title: String,
        done: Boolean
    ): String {
        if (title.isBlank()) {
            return "Nothing changed. No goal title was given."
        }

        val goals = repository.getGoals().first()
        val match = findGoal(goals, title)
            ?: return noMatchingGoal(title, goals)

        if (match.isDone == done) {
            return if (done) {
                "That goal is already completed: \"${match.title}\"."
            } else {
                "That goal is already open: \"${match.title}\"."
            }
        }

        repository.setDone(match, done)

        return if (done) {
            "Completed goal: \"${match.title}\"."
        } else {
            "Reopened goal: \"${match.title}\"."
        }
    }

    private suspend fun deleteGoal(title: String): String {
        if (title.isBlank()) {
            return "Nothing deleted. No goal title was given."
        }

        val goals = repository.getGoals().first()
        val match = findGoal(goals, title)
            ?: return noMatchingGoal(title, goals)

        repository.deleteGoal(match)

        return "Deleted goal: \"${match.title}\"."
    }

    private fun findGoal(
        goals: List<Goal>,
        title: String
    ): Goal? {
        val cleanTitle = title.trim()

        val exact = goals.firstOrNull {
            it.title.equals(cleanTitle, ignoreCase = true)
        }

        if (exact != null) {
            return exact
        }

        val containing = goals.filter {
            it.title.contains(cleanTitle, ignoreCase = true) ||
                    cleanTitle.contains(it.title, ignoreCase = true)
        }

        return if (containing.size == 1) {
            containing.first()
        } else {
            null
        }
    }

    private fun noMatchingGoal(
        title: String,
        goals: List<Goal>
    ): String {
        if (goals.isEmpty()) {
            return "No goal named \"$title\" exists because the goal list is empty."
        }

        val openGoals = goals
            .filter { !it.isDone }
            .map { it.title }

        if (openGoals.isEmpty()) {
            return "I couldn't find an open goal named \"$title\"."
        }

        return "I couldn't uniquely identify \"$title\". Open goals are: " +
                openGoals.joinToString(", ") + "."
    }

    private fun parseTimeline(
        timeline: String,
        now: Long
    ): Long? {
        val text = timeline
            .trim()
            .lowercase(Locale.getDefault())

        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
        }

        when {
            text == "tomorrow" -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return endOfDay(calendar)
            }

            text == "next week" -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                return endOfDay(calendar)
            }

            text == "next month" -> {
                calendar.add(Calendar.MONTH, 1)
                return endOfDay(calendar)
            }

            text == "next year" -> {
                calendar.add(Calendar.YEAR, 1)
                return endOfDay(calendar)
            }

            text == "this week" -> {
                val day = calendar.get(Calendar.DAY_OF_WEEK)
                val daysUntilSunday =
                    Calendar.SUNDAY - day

                if (daysUntilSunday <= 0) {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }

                return endOfDay(calendar)
            }

            text.startsWith("next ") -> {
                val weekday = parseWeekday(text.removePrefix("next ").trim())

                if (weekday != null) {
                    return nextWeekday(calendar, weekday)
                }
            }

            text.startsWith("this ") -> {
                val weekday = parseWeekday(text.removePrefix("this ").trim())

                if (weekday != null) {
                    return thisWeekday(calendar, weekday)
                }
            }

            else -> {
                val weekday = parseWeekday(text)

                if (weekday != null) {
                    return thisWeekday(calendar, weekday)
                }

                val month = parseMonth(text)

                if (month != null) {
                    val currentYear = calendar.get(Calendar.YEAR)
                    val currentMonth = calendar.get(Calendar.MONTH)

                    calendar.set(
                        currentYear,
                        month,
                        1,
                        23,
                        59,
                        59
                    )
                    calendar.set(Calendar.MILLISECOND, 999)

                    if (month <= currentMonth) {
                        calendar.add(Calendar.YEAR, 1)
                    }

                    return endOfDay(calendar)
                }
            }
        }

        return parseExplicitDate(text, calendar)
    }

    private fun parseWeekday(text: String): Int? {
        return when (text) {
            "sunday", "sun" -> Calendar.SUNDAY
            "monday", "mon" -> Calendar.MONDAY
            "tuesday", "tue", "tues" -> Calendar.TUESDAY
            "wednesday", "wed" -> Calendar.WEDNESDAY
            "thursday", "thu", "thur", "thurs" -> Calendar.THURSDAY
            "friday", "fri" -> Calendar.FRIDAY
            "saturday", "sat" -> Calendar.SATURDAY
            else -> null
        }
    }

    private fun thisWeekday(
        calendar: Calendar,
        weekday: Int
    ): Long {
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        var daysAhead = weekday - currentDay

        if (daysAhead < 0) {
            daysAhead += 7
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)

        return endOfDay(calendar)
    }

    private fun nextWeekday(
        calendar: Calendar,
        weekday: Int
    ): Long {
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        var daysAhead = weekday - currentDay

        if (daysAhead <= 0) {
            daysAhead += 7
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)

        return endOfDay(calendar)
    }

    private fun parseMonth(text: String): Int? {
        return when (text) {
            "january", "jan" -> Calendar.JANUARY
            "february", "feb" -> Calendar.FEBRUARY
            "march", "mar" -> Calendar.MARCH
            "april", "apr" -> Calendar.APRIL
            "may" -> Calendar.MAY
            "june", "jun" -> Calendar.JUNE
            "july", "jul" -> Calendar.JULY
            "august", "aug" -> Calendar.AUGUST
            "september", "sep", "sept" -> Calendar.SEPTEMBER
            "october", "oct" -> Calendar.OCTOBER
            "november", "nov" -> Calendar.NOVEMBER
            "december", "dec" -> Calendar.DECEMBER
            else -> null
        }
    }

    private fun parseExplicitDate(
        text: String,
        calendar: Calendar
    ): Long? {
        val formats = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd"
        )

        for (pattern in formats) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.getDefault())
                formatter.isLenient = false
                val parsed = formatter.parse(text)

                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
                // Try the next format.
            }
        }

        return null
    }

    private fun endOfDay(calendar: Calendar): Long {
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(timestamp)
    }


}
