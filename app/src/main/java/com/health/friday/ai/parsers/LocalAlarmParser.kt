
package com.health.friday.ai.parsers

import com.health.friday.ai.AiToolCall
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/*
 * ============================================================================
 * LOCAL ALARM PARSER
 * ============================================================================
 *
 * Responsible only for understanding alarm requests.
 *
 * Supported absolute times:
 *
 *   "set an alarm for 7"
 *   "set an alarm for 12"
 *   "set an alarm for 730"
 *   "set an alarm for 123"
 *   "set an alarm for 1234"
 *   "set an alarm for 7:30"
 *   "set an alarm for 7 PM"
 *   "wake me at 6:45 AM"
 *
 * Compact time rule:
 *
 *   7     -> 07:00
 *   12    -> 12:00
 *   123   -> 01:23
 *   730   -> 07:30
 *   1234  -> 12:34
 *

 */
private val absoluteTime =
    Regex(
        "\\b(?:at|for)\\s+" +
                "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
        RegexOption.IGNORE_CASE
    )

private val compactTime =
    Regex(
        "\\b(?:at|for)\\s+(\\d{1,4})\\b",
        RegexOption.IGNORE_CASE
    )

/*
 * Relative time.
 */
private val relativeTime =
    Regex(
        "\\b(?:in|after)\\s+" +
                "(\\d+(?:\\.\\d+)?)\\s*" +
                "(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b",
        RegexOption.IGNORE_CASE
    )

/*
 * ============================================================================
 * SCHEDULE MARKERS
 * ============================================================================
 */

private val tomorrowWord =
    Regex(
        "\\btomorrow\\b",
        RegexOption.IGNORE_CASE
    )

private val dailyWord =
    Regex(
        "\\b(" +
                "every\\s+day|" +
                "everyday|" +
                "daily|" +
                "each\\s+day|" +
                "every\\s+morning|" +
                "every\\s+night|" +
                "each\\s+morning|" +
                "each\\s+night" +
                ")\\b",
        RegexOption.IGNORE_CASE
    )

/*
 * ============================================================================
 * ALARM INTENT
 * ============================================================================
 */

private val alarmIntent =
    Regex(
        "\\b(" +
                "alarm|" +
                "alarms|" +
                "wake\\s+me|" +
                "wake\\s+up|" +
                "wake-up|" +
                "set\\s+an?\\s+alarm|" +
                "set\\s+my\\s+alarm" +
                ")\\b",
        RegexOption.IGNORE_CASE
    )

/*
 * ============================================================================
 * CANCEL / DISABLE WORDS
 * ============================================================================
 */

private val alarmManagementWord =
    Regex(
        "\\b(" +
                "delete|" +
                "remove|" +
                "cancel|" +
                "disable|" +
                "turn\\s+off|" +
                "stop" +
                ")\\b",
        RegexOption.IGNORE_CASE
    )

class LocalAlarmParser {

    fun parse(
        original: String,
        text: String
    ): AiToolCall? {

        /*
         * =====================================================================
         * SAFETY GATE
         * =====================================================================
         */

        if (
            !alarmIntent.containsMatchIn(text)
        ) {
            return null
        }

        /*
         * Do not create an alarm for management requests.
         */
        if (
            alarmManagementWord.containsMatchIn(text)
        ) {
            return null
        }

        val zone =
            ZoneId.systemDefault()

        val now =
            ZonedDateTime.now(zone)

        val repeatDaily =
            dailyWord.containsMatchIn(text)

        val tomorrow =
            tomorrowWord.containsMatchIn(text)

        /*
         * =====================================================================
         * 1. RELATIVE ALARM
         * =====================================================================
         */

        val relativeMatch =
            relativeTime.find(text)

        if (relativeMatch != null) {

            /*
             * Relative + tomorrow is contradictory.
             */
            if (tomorrow) {
                return null
            }

            /*
             * Relative + daily is ambiguous.
             */
            if (repeatDaily) {
                return null
            }

            val amount =
                relativeMatch.groupValues[1]
                    .toDoubleOrNull()
                    ?: return null

            if (
                amount <= 0
            ) {
                return null
            }

            val unit =
                relativeMatch.groupValues[2]
                    .lowercase(Locale.ROOT)

            val delayMillis =
                when {

                    unit.startsWith("second") ||
                            unit.startsWith("sec") -> {

                        (amount * 1_000.0)
                            .toLong()
                    }

                    unit.startsWith("minute") ||
                            unit.startsWith("min") -> {

                        (amount * 60_000.0)
                            .toLong()
                    }

                    unit.startsWith("hour") ||
                            unit.startsWith("hr") -> {

                        (amount * 3_600_000.0)
                            .toLong()
                    }

                    else -> {
                        return null
                    }
                }

            if (
                delayMillis <= 0
            ) {
                return null
            }

            val triggerTime =
                if (
                    unit.startsWith("second") ||
                    unit.startsWith("sec")
                ) {

                    now.plusNanos(
                        delayMillis * 1_000_000L
                    )

                } else {

                    now
                        .withSecond(0)
                        .withNano(0)
                        .plusNanos(
                            delayMillis * 1_000_000L
                        )
                }

            return createAlarmCall(
                original = original,
                triggerAtMillis =
                    triggerTime
                        .toInstant()
                        .toEpochMilli(),
                repeatDaily = false
            )
        }

        /*
         * =====================================================================
         * 2. STANDARD ABSOLUTE ALARM
         * =====================================================================
         */

        val standardMatch =
            absoluteTime.find(text)

        if (standardMatch != null) {

            val hourValue =
                standardMatch.groupValues[1]
                    .toIntOrNull()
                    ?: return null

            val minuteValue =
                if (
                    standardMatch.groupValues[2].isBlank()
                ) {
                    0
                } else {
                    standardMatch.groupValues[2]
                        .toIntOrNull()
                        ?: return null
                }

            val meridiem =
                standardMatch.groupValues[3]
                    .lowercase(Locale.ROOT)

            val hour =
                convertHour(
                    hourValue = hourValue,
                    minuteValue = minuteValue,
                    meridiem = meridiem
                )
                    ?: return null

            return createAbsoluteAlarm(
                original = original,
                zone = zone,
                now = now,
                hour = hour,
                minute = minuteValue,
                tomorrow = tomorrow,
                repeatDaily = repeatDaily
            )
        }

        /*
         * =====================================================================
         * 3. COMPACT ABSOLUTE ALARM
         * =====================================================================
         *
         * This handles:
         *
         *   730
         *   123
         *   1234
         *
         * by taking the last two digits as minutes.
         */

        val compactMatch =
            compactTime.find(text)

        if (compactMatch != null) {

            val digits =
                compactMatch.groupValues[1]

            val parsed =
                parseCompactTime(
                    digits = digits
                )
                    ?: return null

            return createAbsoluteAlarm(
                original = original,
                zone = zone,
                now = now,
                hour = parsed.first,
                minute = parsed.second,
                tomorrow = tomorrow,
                repeatDaily = repeatDaily
            )
        }

        /*
         * No usable time.
         */
        return null
    }

    /*
     * =========================================================================
     * COMPACT TIME
     * =========================================================================
     *
     * Rule:
     *
     *   7      -> 7:00
     *   12     -> 12:00
     *   123    -> 1:23
     *   730    -> 7:30
     *   1234   -> 12:34
     *
     * Last two digits = minutes.
     * Everything before them = hour.
     */

    private fun parseCompactTime(
        digits: String
    ): Pair<Int, Int>? {

        if (
            digits.isEmpty() ||
            digits.length > 4
        ) {
            return null
        }

        /*
         * One or two digits represent an hour.
         */
        if (
            digits.length <= 2
        ) {

            val hour =
                digits.toIntOrNull()
                    ?: return null

            if (
                hour !in 0..23
            ) {
                return null
            }

            return hour to 0
        }

        /*
         * Three or four digits:
         *
         * last two = minutes
         * preceding digits = hour
         */

        val minuteText =
            digits.takeLast(2)

        val hourText =
            digits.dropLast(2)

        val hour =
            hourText.toIntOrNull()
                ?: return null

        val minute =
            minuteText.toIntOrNull()
                ?: return null

        if (
            hour !in 0..23
        ) {
            return null
        }

        if (
            minute !in 0..59
        ) {
            return null
        }

        return hour to minute
    }

    /*
     * =========================================================================
     * STANDARD HOUR CONVERSION
     * =========================================================================
     */

    private fun convertHour(
        hourValue: Int,
        minuteValue: Int,
        meridiem: String
    ): Int? {

        if (
            minuteValue !in 0..59
        ) {
            return null
        }

        return when {

            meridiem == "am" -> {

                if (
                    hourValue !in 1..12
                ) {
                    null
                } else if (
                    hourValue == 12
                ) {
                    0
                } else {
                    hourValue
                }
            }

            meridiem == "pm" -> {

                if (
                    hourValue !in 1..12
                ) {
                    null
                } else if (
                    hourValue == 12
                ) {
                    12
                } else {
                    hourValue + 12
                }
            }

            else -> {

                if (
                    hourValue !in 0..23
                ) {
                    null
                } else {
                    hourValue
                }
            }
        }
    }

    /*
     * =========================================================================
     * ABSOLUTE ALARM CREATION
     * =========================================================================
     */

    private fun createAbsoluteAlarm(
        original: String,
        zone: ZoneId,
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        tomorrow: Boolean,
        repeatDaily: Boolean
    ): AiToolCall? {

        if (
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            return null
        }

        val requestedTime =
            LocalTime.of(
                hour,
                minute
            )

        /*
         * =====================================================================
         * TOMORROW
         * =====================================================================
         */

        if (tomorrow) {

            /*
             * "tomorrow" + "daily" is contradictory.
             */
            if (repeatDaily) {
                return null
            }

            val tomorrowDate =
                LocalDate.now(zone)
                    .plusDays(1)

            val trigger =
                tomorrowDate
                    .atTime(requestedTime)
                    .atZone(zone)

            return createAlarmCall(
                original = original,
                triggerAtMillis =
                    trigger
                        .toInstant()
                        .toEpochMilli(),
                repeatDaily = false
            )
        }

        /*
         * =====================================================================
         * DAILY
         * =====================================================================
         */

        if (repeatDaily) {

            val today =
                LocalDate.now(zone)

            val trigger =
                today
                    .atTime(requestedTime)
                    .atZone(zone)

            return createAlarmCall(
                original = original,
                triggerAtMillis =
                    trigger
                        .toInstant()
                        .toEpochMilli(),
                repeatDaily = true
            )
        }

        /*
         * =====================================================================
         * ONE TIME
         * =====================================================================
         */

        var alarmDate =
            LocalDate.now(zone)

        var trigger =
            alarmDate
                .atTime(requestedTime)
                .atZone(zone)

        /*
         * If today's time has already passed,
         * use tomorrow.
         */

        if (
            !trigger.isAfter(now)
        ) {

            alarmDate =
                alarmDate.plusDays(1)

            trigger =
                alarmDate
                    .atTime(requestedTime)
                    .atZone(zone)
        }

        return createAlarmCall(
            original = original,
            triggerAtMillis =
                trigger
                    .toInstant()
                    .toEpochMilli(),
            repeatDaily = false
        )
    }

    /*
     * =========================================================================
     * CREATE TOOL CALL
     * =========================================================================
     */

    private fun createAlarmCall(
        original: String,
        triggerAtMillis: Long,
        repeatDaily: Boolean
    ): AiToolCall {

        val title =
            extractAlarmTitle(
                original = original
            )

        return AiToolCall(
            name = "set_alarm",
            arguments = mapOf(
                "title" to title,
                "timeMillis" to triggerAtMillis.toString(),
                "repeatDaily" to repeatDaily.toString()
            )
        )
    }

    /*
     * =========================================================================
     * TITLE EXTRACTION
     * =========================================================================
     */

    private fun extractAlarmTitle(
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
                    "(?i)\\b(set|create|make|put|schedule)\\b"
                ),
                ""
            )

        /*
         * Alarm phrases.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(an?|the|my)\\s+alarm\\b"
                ),
                ""
            )

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(alarm|wake\\s+me|wake-up|wake\\s+up)\\b"
                ),
                ""
            )

        /*
         * Standard time.
         */

        cleaned =
            cleaned.replace(
                absoluteTime,
                ""
            )

        /*
         * Compact time.
         */

        cleaned =
            cleaned.replace(
                compactTime,
                ""
            )

        /*
         * Relative time.
         */

        cleaned =
            cleaned.replace(
                relativeTime,
                ""
            )

        /*
         * Schedule modifiers.
         */

        cleaned =
            cleaned.replace(
                tomorrowWord,
                ""
            )

        cleaned =
            cleaned.replace(
                dailyWord,
                ""
            )

        /*
         * Connectors.
         */

        cleaned =
            cleaned.replace(
                Regex(
                    "(?i)\\b(for|at|on|in|after)\\b"
                ),
                ""
            )

        return cleanAlarmTitle(
            cleaned
        )
    }

    private fun cleanAlarmTitle(
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
            .ifBlank {
                "Alarm"
            }
    }
}

