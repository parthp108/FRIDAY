package com.health.friday.reminders

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.Reminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class ReminderTool(
    private val repository: ReminderRepository
) : AiTool {


    override val name = "reminder"

    override val description =
        "Manages the user's reminders. Can list, add, enable, " +
                "disable, or delete reminders. Reminders can use exact times " +
                "such as 08:00 or relative times such as in 10 minutes."

    override val parameters = listOf(
        AiToolParameter(
            name = "action",
            description =
                "What to do: list, add, enable, disable, or delete.",
            required = true
        ),
        AiToolParameter(
            name = "title",
            description =
                "The reminder title. Required for add, enable, disable, and delete."
        ),
        AiToolParameter(
            name = "time",
            description =
                "The reminder time for add. Can be an exact time such as " +
                        "08:00 or 21:30, or a relative time such as " +
                        "in 1 minute, in 30 minutes, or in 2 hours."
        ),
        AiToolParameter(
            name = "repeatDaily",
            description =
                "Whether the reminder should repeat every day. " +
                        "Use true or false. Defaults to true."
        )
    )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val action =
            arguments["action"]
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val title =
            arguments["title"]
                ?.trim()
                .orEmpty()

        return when (action) {

            "list" -> {
                listReminders()
            }

            "add" -> {
                addReminder(
                    title = title,
                    time = arguments["time"]
                        ?.trim()
                        .orEmpty(),
                    repeatDaily =
                        arguments["repeatDaily"]
                            ?.trim()
                            ?.lowercase()
                            ?.let { it == "true" }
                            ?: true
                )
            }

            "enable" -> {
                changeReminderState(
                    title = title,
                    enabled = true
                )
            }

            "disable" -> {
                changeReminderState(
                    title = title,
                    enabled = false
                )
            }

            "delete" -> {
                deleteReminder(title)
            }

            else -> {
                "Nothing changed. The reminder action must be " +
                        "list, add, enable, disable, or delete."
            }
        }
    }

    private suspend fun listReminders(): String {

        val reminders =
            repository.getRemindersNow()

        if (reminders.isEmpty()) {
            return "There are no reminders."
        }

        return buildString {

            append("Reminders:\n")

            for (reminder in reminders) {

                val time =
                    formatDateTime(reminder.timeMillis)

                val status =
                    if (reminder.enabled) {
                        "enabled"
                    } else {
                        "disabled"
                    }

                val repeat =
                    if (reminder.repeatDaily) {
                        "daily"
                    } else {
                        "once"
                    }

                append(
                    "• ${reminder.title} — $time, $repeat, $status\n"
                )
            }

        }.trimEnd()
    }

    private suspend fun addReminder(
        title: String,
        time: String,
        repeatDaily: Boolean
    ): String {

        if (title.isBlank()) {
            return "Nothing added. No reminder title was given."
        }

        if (time.isBlank()) {
            return "Nothing added. No reminder time was given."
        }

        val timeMillis =
            parseTime(time)

        if (timeMillis == null) {
            return "Nothing added. I couldn't understand the reminder time \"$time\". Use a time such as 08:00, 21:30, or in 10 minutes."
        }

        val existing =
            repository.getRemindersNow()

        val duplicate =
            existing.firstOrNull {
                it.title.equals(
                    title.trim(),
                    ignoreCase = true
                ) &&
                        it.timeMillis == timeMillis &&
                        it.repeatDaily == repeatDaily
            }

        if (duplicate != null) {
            return "That reminder already exists: \"${duplicate.title}\" at ${formatDateTime(duplicate.timeMillis)}."
        }

        val saved =
            repository.addReminder(
                title = title,
                timeMillis = timeMillis,
                repeatDaily = repeatDaily
            )

        if (saved == null) {
            return "Nothing added. The reminder title was blank."
        }

        if (!saved.enabled) {
            return "The reminder was saved but could not be scheduled. Exact alarm permission may be disabled."
        }

        val repeatText =
            if (saved.repeatDaily) {
                "daily"
            } else {
                "once"
            }

        return "Added reminder: \"${saved.title}\" at ${formatDateTime(saved.timeMillis)}, $repeatText."
    }

    private suspend fun changeReminderState(
        title: String,
        enabled: Boolean
    ): String {

        if (title.isBlank()) {
            return "Nothing changed. No reminder title was given."
        }

        val reminders =
            repository.getRemindersNow()

        val match =
            findReminder(
                reminders = reminders,
                title = title
            )

        if (match == null) {
            return noMatchingReminder(
                title = title,
                reminders = reminders
            )
        }

        if (match.enabled == enabled) {
            return if (enabled) {
                "That reminder is already enabled: \"${match.title}\"."
            } else {
                "That reminder is already disabled: \"${match.title}\"."
            }
        }

        if (enabled) {

            val scheduled =
                repository.enableReminder(match)

            if (!scheduled) {
                return "I couldn't enable \"${match.title}\" because exact alarm permission is disabled."
            }

            return "Enabled reminder: \"${match.title}\"."
        }

        repository.disableReminder(match)

        return "Disabled reminder: \"${match.title}\"."
    }

    private suspend fun deleteReminder(
        title: String
    ): String {

        if (title.isBlank()) {
            return "Nothing deleted. No reminder title was given."
        }

        val reminders =
            repository.getRemindersNow()

        val match =
            findReminder(
                reminders = reminders,
                title = title
            )

        if (match == null) {
            return noMatchingReminder(
                title = title,
                reminders = reminders
            )
        }

        repository.deleteReminder(match)

        return "Deleted reminder: \"${match.title}\"."
    }

    private fun findReminder(
        reminders: List<Reminder>,
        title: String
    ): Reminder? {

        val cleanTitle =
            title.trim()

        val exact =
            reminders.firstOrNull {
                it.title.equals(
                    cleanTitle,
                    ignoreCase = true
                )
            }

        if (exact != null) {
            return exact
        }

        val containing =
            reminders.filter {
                it.title.contains(
                    cleanTitle,
                    ignoreCase = true
                ) ||
                        cleanTitle.contains(
                            it.title,
                            ignoreCase = true
                        )
            }

        return if (containing.size == 1) {
            containing.first()
        } else {
            null
        }
    }

    private fun noMatchingReminder(
        title: String,
        reminders: List<Reminder>
    ): String {

        if (reminders.isEmpty()) {
            return "No reminder named \"$title\" exists because the reminder list is empty."
        }

        return "I couldn't uniquely identify \"$title\". " +
                "Reminders are: ${reminders.joinToString(", ") { it.title }}."
    }

    private fun parseTime(
        value: String
    ): Long? {

        val cleanValue =
            value.trim().lowercase(Locale.getDefault())

        val relativePattern =
            Pattern.compile(
                "^(?:in|after)\\s+(\\d+)\\s+(minute|minutes|min|mins|hour|hours|hr|hrs)$"
            )

        val relativeMatch =
            relativePattern.matcher(cleanValue)

        if (relativeMatch.matches()) {

            val amount =
                relativeMatch.group(1)
                    ?.toLongOrNull()
                    ?: return null

            val unit =
                relativeMatch.group(2)
                    ?: return null

            if (amount <= 0) {
                return null
            }

            return Calendar.getInstance().apply {

                when (unit) {

                    "minute",
                    "minutes",
                    "min",
                    "mins" -> {
                        add(
                            Calendar.MINUTE,
                            amount.toInt()
                        )
                    }

                    "hour",
                    "hours",
                    "hr",
                    "hrs" -> {
                        add(
                            Calendar.HOUR_OF_DAY,
                            amount.toInt()
                        )
                    }

                    else -> {
                        return null
                    }
                }

            }.timeInMillis
        }

        val formatter =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).apply {
                isLenient = false
            }

        val parsed =
            try {
                formatter.parse(value)
            } catch (_: Exception) {
                null
            } ?: return null

        val parsedCalendar =
            Calendar.getInstance().apply {
                time = parsed
            }

        val now =
            Calendar.getInstance()

        return Calendar.getInstance().apply {

            set(
                Calendar.HOUR_OF_DAY,
                parsedCalendar.get(Calendar.HOUR_OF_DAY)
            )

            set(
                Calendar.MINUTE,
                parsedCalendar.get(Calendar.MINUTE)
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )

            if (timeInMillis <= now.timeInMillis) {
                add(
                    Calendar.DAY_OF_YEAR,
                    1
                )
            }

        }.timeInMillis
    }

    private fun formatDateTime(
        timeMillis: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM, HH:mm",
            Locale.getDefault()
        ).format(timeMillis)
    }


}
