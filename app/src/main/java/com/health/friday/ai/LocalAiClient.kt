
package com.health.friday.ai

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val questionStart =
    Regex(
        "^(what|how|did|do|does|is|are|can|should|have|has|why|when|which)\\b"
    )

private val eatVerb =
    Regex("\\b(ate|eaten|eating|eat)\\b")

private val mealWord =
    Regex("\\b(breakfast|lunch|dinner|supper|snack)\\b")

private val waterWord =
    Regex("\\bwater\\b")

private val nutritionWord =
    Regex(
        "\\b(eat|ate|eaten|calories|calorie|kcal|protein|carbs|carb|fat|" +
                "macros|macro|nutrition|food|meals|meal|water|overeat\\w*)\\b"
    )

private val greetingWord =
    Regex("\\b(hello|hi|hey)\\b")

private val weekWord =
    Regex("\\b(week|weekly|7 days|seven days)\\b")

private val dayBeforeYesterday =
    Regex("\\bday before yesterday\\b")

private val yesterdayWord =
    Regex("\\byesterday\\b")

private val daysAgo =
    Regex("\\b(\\d+)\\s+days?\\s+ago\\b")

private val waterAmount =
    Regex(
        "(\\d+(?:\\.\\d+)?)\\s*" +
                "(ml|millilit(?:er|re)s?|l|lit(?:er|re)s?|glass(?:es)?|bottles?|cups?)\\b"
    )

private val articleBeforeUnit =
    Regex(
        "\\b(?:a|an|one)\\s+(?=(?:glass|glasses|bottle|bottles|cup|cups|litre|liter)\\b)"
    )

private val alarmWord =
    Regex(
        "\\b(alarm|alarms|wake me|wake-up|wake up)\\b"
    )

private val alarmTime =
    Regex(
        "\\b(?:at|for)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b"
    )
private val relativeAlarm =
    Regex(
        "\\b(?:in|after|for)\\s+" +
                "(\\d+(?:\\.\\d+)?)\\s*" +
                "(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b"
    )

private val tomorrowWord =
    Regex("\\btomorrow\\b")

private val dailyAlarmWord =
    Regex(
        "\\b(every day|everyday|daily|each day|every night|every morning)\\b"
    )

class LocalAiClient : AiClient {

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse {

        // Second pass: the tools already ran, so this offline "AI"
        // simply relays their results.
        if (messages.lastOrNull()?.role == "tool") {

            val results =
                messages
                    .takeLastWhile {
                        it.role == "tool"
                    }
                    .joinToString("\n\n") {
                        it.content
                    }

            return AiResponse(
                message = results
            )
        }

        val original =
            messages
                .lastOrNull {
                    it.role == "user"
                }
                ?.content
                ?.trim()
                ?: ""

        val text =
            original.lowercase()

        val isQuestion =
            text.endsWith("?") ||
                    questionStart.containsMatchIn(text)

        /*
         * Alarm commands are action requests, not questions.
         */
        if (!isQuestion && alarmWord.containsMatchIn(text)) {

            val alarmCall =
                createAlarmToolCall(
                    original = original,
                    text = text
                )

            if (alarmCall != null) {
                return AiResponse(
                    message = "",
                    toolCalls = listOf(alarmCall)
                )
            }

            return AiResponse(
                message =
                    "Tell me the alarm time, for example " +
                            "\"set an alarm for 7:30 AM\" or " +
                            "\"wake me in 10 minutes\"."
            )
        }

        if (!isQuestion) {

            val toolCalls =
                mutableListOf<AiToolCall>()

            val date =
                extractDate(text)

            val waterMl =
                if (waterWord.containsMatchIn(text)) {
                    extractWaterMl(text)
                } else {
                    null
                }

            if (waterMl != null) {

                val args =
                    mutableMapOf(
                        "amount_ml" to waterMl.toString()
                    )

                if (date != null) {
                    args["date"] = date
                }

                toolCalls.add(
                    AiToolCall(
                        name = "log_water",
                        arguments = args
                    )
                )
            }

            val looksLikeMeal =
                eatVerb.containsMatchIn(text) ||
                        (
                                mealWord.containsMatchIn(text) &&
                                        Regex("\\d").containsMatchIn(text)
                                )

            if (looksLikeMeal) {

                val args =
                    mutableMapOf(
                        "text" to original
                    )

                if (date != null) {
                    args["date"] = date
                }

                toolCalls.add(
                    AiToolCall(
                        name = "log_meal",
                        arguments = args
                    )
                )
            }

            if (toolCalls.isNotEmpty()) {
                return AiResponse(
                    message = "",
                    toolCalls = toolCalls
                )
            }
        }

        if (weekWord.containsMatchIn(text)) {
            return AiResponse(
                message = "",
                toolCalls = listOf(
                    AiToolCall(
                        name = "get_week_nutrition"
                    )
                )
            )
        }

        if (nutritionWord.containsMatchIn(text)) {
            return AiResponse(
                message = "",
                toolCalls = listOf(
                    AiToolCall(
                        name = "get_today_nutrition"
                    )
                )
            )
        }

        val reply =
            when {

                greetingWord.containsMatchIn(text) ->
                    "FRIDAY online. What do you need?"

                text.contains("who are you") ->
                    "I'm FRIDAY. Your personal assistant."

                Regex("\\bhealth\\b")
                    .containsMatchIn(text) ->
                    "Health data isn't connected yet."

                text.contains("screen time") ->
                    "Screen time is on the Tasks tab. " +
                            "I can't answer questions about it yet."

                else ->
                    "I'm in offline mode, so I only understand a few things. " +
                            "Tell me what you ate (\"I ate 2 eggs and 2 slices of toast\"), " +
                            "what you drank (\"drank 500 ml water\"), or ask what you've had today. " +
                            "A full AI isn't connected yet."
            }

        return AiResponse(
            message = reply
        )
    }

    private fun createAlarmToolCall(
        original: String,
        text: String
    ): AiToolCall? {

        val zone =
            ZoneId.systemDefault()

        val now =
            java.time.ZonedDateTime.now(zone)

        /*
         * First check for relative alarms:
         *
         * "in 10 minutes"
         * "after 30 minutes"
         * "for 1 minute"
         * "for 10 minutes"
         * "in 2 hours"
         * "after 45 seconds"
         */
        val relativeMatch =
            relativeAlarm.find(text)

        val timeMillis: Long

        if (relativeMatch != null) {

            val amount =
                relativeMatch.groupValues[1]
                    .toDoubleOrNull()
                    ?: return null

            if (amount <= 0) {
                return null
            }

            val unit =
                relativeMatch.groupValues[2]
                    .lowercase()

            val delayMillis =
                when {
                    unit.startsWith("second") ||
                            unit.startsWith("sec") ->
                        (amount * 1_000.0).toLong()

                    unit.startsWith("minute") ||
                            unit.startsWith("min") ->
                        (amount * 60_000.0).toLong()

                    unit.startsWith("hour") ||
                            unit.startsWith("hr") ->
                        (amount * 3_600_000.0).toLong()

                    else ->
                        return null
                }

            if (delayMillis <= 0) {
                return null
            }

            /*
             * Minute/hour alarms ignore the current seconds.
             *
             * Example:
             * 12:25:26 + 1 minute -> 12:26:00
             * 12:25:59 + 1 minute -> 12:26:00
             *
             * Second-based alarms still use the exact current time.
             */
            timeMillis =
                if (
                    unit.startsWith("minute") ||
                    unit.startsWith("min") ||
                    unit.startsWith("hour") ||
                    unit.startsWith("hr")
                ) {

                    val baseTime =
                        now
                            .withSecond(0)
                            .withNano(0)

                    baseTime
                        .plusNanos(
                            delayMillis * 1_000_000L
                        )
                        .toInstant()
                        .toEpochMilli()

                } else {

                    now
                        .plusNanos(
                            delayMillis * 1_000_000L
                        )
                        .toInstant()
                        .toEpochMilli()
                }

        } else {

            /*
             * Otherwise look for an absolute clock time:
             *
             * "at 7"
             * "at 7:30"
             * "at 7 PM"
             * "at 7:30 PM"
             */
            val match =
                alarmTime.find(text)
                    ?: return null

            val hourValue =
                match.groupValues[1]
                    .toIntOrNull()
                    ?: return null

            val minuteValue =
                if (match.groupValues[2].isEmpty()) {
                    0
                } else {
                    match.groupValues[2]
                        .toIntOrNull()
                        ?: return null
                }

            val meridiem =
                match.groupValues[3]
                    .lowercase()

            var hour =
                hourValue

            if (minuteValue !in 0..59) {
                return null
            }

            if (meridiem.isNotEmpty()) {

                if (hour !in 1..12) {
                    return null
                }

                hour =
                    when (meridiem) {
                        "am" ->
                            if (hour == 12) 0 else hour

                        "pm" ->
                            if (hour == 12) 12 else hour + 12

                        else ->
                            return null
                    }

            } else {

                if (hour !in 0..23) {
                    return null
                }
            }

            var alarmDate =
                LocalDate.now(zone)

            val requestedTime =
                LocalTime.of(
                    hour,
                    minuteValue
                )

            var alarmDateTime =
                alarmDate.atTime(requestedTime)

            val tomorrow =
                tomorrowWord.containsMatchIn(text)

            val repeatDaily =
                dailyAlarmWord.containsMatchIn(text)

            if (tomorrow) {

                alarmDate =
                    alarmDate.plusDays(1)

                alarmDateTime =
                    alarmDate.atTime(requestedTime)

            } else if (
                !repeatDaily &&
                !alarmDateTime.isAfter(now.toLocalDateTime())
            ) {

                /*
                 * If today's requested time has already passed,
                 * schedule the one-time alarm for tomorrow.
                 */
                alarmDate =
                    alarmDate.plusDays(1)

                alarmDateTime =
                    alarmDate.atTime(requestedTime)
            }

            timeMillis =
                alarmDateTime
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
        }

        val repeatDaily =
            dailyAlarmWord.containsMatchIn(text)

        val title =
            extractAlarmTitle(
                original
            )

        return AiToolCall(
            name = "set_alarm",
            arguments =
                mapOf(
                    "title" to title,
                    "timeMillis" to timeMillis.toString(),
                    "repeatDaily" to repeatDaily.toString()
                )
        )
    }

    private fun extractAlarmTitle(
        original: String
    ): String {

        val cleaned =
            original
                .replace(
                    Regex(
                        "(?i)\\b(set|create|make|put|schedule)\\b"
                    ),
                    ""
                )
                .replace(
                    Regex(
                        "(?i)\\b(an?|the)\\s+alarm\\b"
                    ),
                    ""
                )
                .replace(
                    Regex(
                        "(?i)\\b(alarm|wake me|wake-up|wake up)\\b"
                    ),
                    ""
                )
                .replace(
                    alarmTime,
                    ""
                )
                .replace(
                    relativeAlarm,
                    ""
                )
                .replace(
                    tomorrowWord,
                    ""
                )
                .replace(
                    dailyAlarmWord,
                    ""
                )
                .replace(
                    Regex(
                        "(?i)\\b(for|at|on)\\b"
                    ),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim(
                    ' ',
                    '.',
                    ',',
                    '!'
                )

        return if (cleaned.isEmpty()) {
            "Alarm"
        } else {
            cleaned
        }
    }

    private fun extractDate(
        text: String
    ): String? {

        if (dayBeforeYesterday.containsMatchIn(text)) {
            return "day before yesterday"
        }

        if (yesterdayWord.containsMatchIn(text)) {
            return "yesterday"
        }

        val ago =
            daysAgo.find(text)

        if (ago != null) {
            return "${ago.groupValues[1]} days ago"
        }

        return null
    }

    // glass and cup count as 250 ml, a bottle as 500 ml.
    private fun extractWaterMl(
        text: String
    ): Int? {

        val prepared =
            articleBeforeUnit.replace(
                text,
                "1 "
            )

        val match =
            waterAmount.find(prepared)
                ?: return null

        val amount =
            match.groupValues[1]
                .toDoubleOrNull()
                ?: return null

        val unit =
            match.groupValues[2]

        val ml =
            when {
                unit == "ml" ||
                        unit.startsWith("milli") ->
                    amount

                unit == "l" ||
                        unit.startsWith("lit") ->
                    amount * 1000

                unit.startsWith("glass") ->
                    amount * 250

                unit.startsWith("bottle") ->
                    amount * 500

                unit.startsWith("cup") ->
                    amount * 250

                else ->
                    return null
            }

        val rounded =
            Math.round(ml).toInt()

        return if (rounded > 0) {
            rounded
        } else {
            null
        }
    }
}

