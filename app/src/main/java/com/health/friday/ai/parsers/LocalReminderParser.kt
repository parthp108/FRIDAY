
package com.health.friday.ai.parsers

import com.health.friday.ai.AiToolCall

class LocalReminderParser {

    /*
     * ========================================================================
     * REMINDER INTENT
     * ========================================================================
     */

    private val reminderWord =
        Regex(
            "\\b(reminder|reminders|remind me)\\b"
        )

    private val reminderAddWord =
        Regex(
            "\\b(add|create|make|set|schedule|remind)\\b"
        )

    private val reminderEnableWord =
        Regex(
            "\\b(enable|turn on|activate|resume|reactivate)\\b"
        )

    private val reminderDisableWord =
        Regex(
            "\\b(disable|turn off|deactivate|pause|stop)\\b"
        )

    private val reminderDeleteWord =
        Regex(
            "\\b(delete|remove|erase|drop|cancel)\\b"
        )

    private val reminderListWord =
        Regex(
            "\\b(list|show|see|view|check|display)\\b"
        )

    /*
     * ========================================================================
     * TIME
     * ========================================================================
     */

    private val reminderTime =
        Regex(
            "\\b(?:at|for)\\s+" +
                    "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b"
        )

    private val relativeReminder =
        Regex(
            "\\b(?:in|after)\\s+" +
                    "(\\d+)\\s*" +
                    "(minutes?|mins?|hours?|hrs?)\\b"
        )

    /*
     * ========================================================================
     * EXPLICIT DAILY WORDS
     * ========================================================================
     *
     * A reminder is NEVER daily by default.
     *
     * These words are the only things that make the current reminder
     * repeat every day.
     */

    private val dailyReminderWord =
        Regex(
            "\\b(" +
                    "every\\s+day|" +
                    "everyday|" +
                    "daily|" +
                    "each\\s+day|" +
                    "every\\s+morning|" +
                    "every\\s+night" +
                    ")\\b"
        )

    /*
     * ========================================================================
     * PUBLIC
     * ========================================================================
     */

    fun isReminderRequest(
        text: String
    ): Boolean {

        return reminderWord.containsMatchIn(text)
    }

    fun parse(
        original: String,
        text: String,
        isQuestion: Boolean
    ): AiToolCall? {

        if (
            !isReminderRequest(text)
        ) {
            return null
        }

        val action =
            when {

                reminderDeleteWord.containsMatchIn(text) ->
                    "delete"

                reminderDisableWord.containsMatchIn(text) ->
                    "disable"

                reminderEnableWord.containsMatchIn(text) ->
                    "enable"

                reminderListWord.containsMatchIn(text) &&
                        isQuestion ->
                    "list"

                Regex(
                    ".*\\b(show|list|what|which|see|view)\\b.*" +
                            "\\b(reminder|reminders)\\b.*"
                ).matches(text) ->
                    "list"

                reminderAddWord.containsMatchIn(text) &&
                        !isQuestion ->
                    "add"

                else ->
                    null
            }
                ?: return null

        /*
         * =====================================================================
         * LIST
         * =====================================================================
         */

        if (
            action == "list"
        ) {

            return AiToolCall(
                name = "reminder",
                arguments = mapOf(
                    "action" to "list"
                )
            )
        }

        /*
         * =====================================================================
         * TITLE
         * =====================================================================
         */

        val title =
            extractReminderTitle(
                original = original
            )

        if (
            title.isBlank()
        ) {
            return null
        }

        /*
         * =====================================================================
         * ENABLE / DISABLE / DELETE
         * =====================================================================
         *
         * Existing reminders do not need a new time.
         */

        if (
            action == "enable" ||
            action == "disable" ||
            action == "delete"
        ) {

            return AiToolCall(
                name = "reminder",
                arguments = mapOf(
                    "action" to action,
                    "title" to title
                )
            )
        }

        /*
         * =====================================================================
         * ADD
         * =====================================================================
         *
         * An ADD reminder requires a time.
         *
         * We never invent a time.
         */

        val time =
            extractReminderTime(text)

        if (
            time.isNullOrBlank()
        ) {
            return null
        }

        /*
         * =====================================================================
         * REPEAT RULE
         * =====================================================================
         *
         * IMPORTANT:
         *
         * Default = ONE TIME.
         *
         * Only explicit daily language makes it repeat daily.
         */

        val repeatDaily =
            dailyReminderWord.containsMatchIn(text)

        return AiToolCall(
            name = "reminder",
            arguments = mapOf(
                "action" to "add",
                "title" to title,
                "time" to time,
                "repeatDaily" to repeatDaily.toString()
            )
        )
    }

    /*
     * ========================================================================
     * NEEDS TIME
     * ========================================================================
     */

    fun needsTime(
        text: String,
        isQuestion: Boolean
    ): Boolean {

        if (
            !isReminderRequest(text)
        ) {
            return false
        }

        if (
            isQuestion
        ) {
            return false
        }

        if (
            !reminderAddWord.containsMatchIn(text)
        ) {
            return false
        }

        if (
            reminderDeleteWord.containsMatchIn(text) ||
            reminderDisableWord.containsMatchIn(text) ||
            reminderEnableWord.containsMatchIn(text)
        ) {
            return false
        }

        return extractReminderTime(text).isNullOrBlank()
    }

    /*
     * ========================================================================
     * TITLE
     * ========================================================================
     */

    private fun extractReminderTitle(
        original: String
    ): String {

        var cleaned =
            original

        /*
         * Commands.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*(please\\s+)?" +
                            "(add|create|make|set|schedule|remind|remember)\\s+"
                ),
                ""
            )

        /*
         * Reminder word.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(a|an|the)?\\s*reminders?\\b"
                ),
                ""
            )

        /*
         * Management words.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(enable|disable|turn|on|off|activate|deactivate|" +
                            "delete|remove|erase|drop|cancel|please)\\b"
                ),
                ""
            )

        /*
         * Time.
         */

        cleaned =
            cleaned.replace(
                reminderTime,
                ""
            )

        cleaned =
            cleaned.replace(
                relativeReminder,
                ""
            )

        /*
         * Daily words.
         */

        cleaned =
            cleaned.replace(
                dailyReminderWord,
                ""
            )

        /*
         * Connectors.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(at|for|in|after)\\b"
                ),
                ""
            )

        /*
         * "remind me to ..."
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*me\\s+to\\s+"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)^\\s*to\\s+"
                ),
                ""
            )

        return cleanExtractedText(
            cleaned
        )
    }

    /*
     * ========================================================================
     * TIME EXTRACTION
     * ========================================================================
     */

    private fun extractReminderTime(
        text: String
    ): String? {

        /*
         * Relative time gets priority.
         *
         * Example:
         *
         * "remind me to drink water after 30 minutes"
         *
         * -> "after 30 minutes"
         */

        val relative =
            relativeReminder.find(text)

        if (
            relative != null
        ) {
            return relative.value
        }

        /*
         * Absolute time.
         *
         * Examples:
         *
         * "at 3 PM"
         * "at 15:30"
         */

        val absolute =
            reminderTime.find(text)

        if (
            absolute != null
        ) {

            val hour =
                absolute.groupValues[1]

            val minute =
                absolute.groupValues[2]

            val meridiem =
                absolute.groupValues[3]

            return buildString {

                append(hour)

                if (
                    minute.isNotEmpty()
                ) {
                    append(":")
                    append(minute)
                }

                if (
                    meridiem.isNotEmpty()
                ) {
                    append(" ")
                    append(meridiem)
                }
            }
        }

        return null
    }

    /*
     * ========================================================================
     * CLEAN TITLE
     * ========================================================================
     */

    private fun cleanExtractedText(
        value: String
    ): String {

        return value
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim(
                ' ',
                '.',
                ',',
                '!',
                '?',
                ':',
                ';',
                '-'
            )
    }
}
