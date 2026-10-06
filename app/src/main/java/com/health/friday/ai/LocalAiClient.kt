
package com.health.friday.ai

import com.health.friday.ai.parsers.LocalAlarmParser
import com.health.friday.ai.parsers.LocalNutritionParser
import com.health.friday.ai.parsers.LocalReminderParser
import com.health.friday.ai.parsers.LocalTaskParser
import java.util.Locale

/*
 * ============================================================================
 * TEXT NORMALIZATION
 * ============================================================================
 */

private fun normalize(
    value: String
): String {

    return value
        .lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/*
 * ============================================================================
 * QUESTION DETECTION
 * ============================================================================
 *
 * This is deliberately broader than simply checking for '?'.
 *
 * Examples:
 *
 * "what did I eat"
 * "show my nutrition"
 * "how much water did I drink"
 * "did I log lunch"
 * "tell me today's calories"
 */

private val questionStart =
    Regex(
        "^(what|how|did|do|does|is|are|can|could|should|have|has|had|" +
                "why|when|which|show|list|tell|give|where|who|am|was|were|" +
                "will|would|may|might)\\b"
    )

private val conversationalQuestion =
    Regex(
        "\\b(what|how|did|do|does|is|are|can|could|should|have|has|" +
                "why|when|which|show|list|tell|give|where|who)\\b"
    )

/*
 * ============================================================================
 * GREETINGS / BASIC CONVERSATION
 * ============================================================================
 */

private val greetingWord =
    Regex(
        "\\b(hello|hi|hey|hiya|good morning|good afternoon|good evening|" +
                "good night)\\b"
    )

private val identityQuestion =
    Regex(
        "\\b(who are you|what are you|what is friday|who is friday)\\b"
    )

private val capabilityQuestion =
    Regex(
        "\\b(what can you do|what do you do|what are your capabilities|" +
                "what can friday do|what can you help with)\\b"
    )

private val thanksWord =
    Regex(
        "\\b(thanks|thank you|thx|appreciate it|much appreciated)\\b"
    )

private val goodbyeWord =
    Regex(
        "\\b(bye|goodbye|see you|good night)\\b"
    )

/*
 * ============================================================================
 * DOMAIN MARKERS
 * ============================================================================
 *
 * These are routing signals only.
 * Actual interpretation belongs to the individual parser.
 */

private val alarmWord =
    Regex(
        "\\b(alarm|alarms|wake me|wake-up|wake up)\\b"
    )

private val reminderWord =
    Regex(
        "\\b(reminder|reminders|remind me)\\b"
    )

private val taskWord =
    Regex(
        "\\b(todo|todos|task|tasks|goal|goals|target|targets)\\b"
    )

private val nutritionWord =
    Regex(
        "\\b(food|meal|meals|breakfast|lunch|dinner|snack|nutrition|" +
                "calories|calorie|kcal|protein|proteins|carb|carbs|fat|fats|" +
                "macro|macros|water|hydration)\\b"
    )

/*
 * ============================================================================
 * LOCAL AI ROUTER
 * ============================================================================
 */

class LocalAiClient : AiClient {

    private val taskParser =
        LocalTaskParser()

    private val reminderParser =
        LocalReminderParser()

    private val nutritionParser =
        LocalNutritionParser()

    private val alarmParser =
        LocalAlarmParser()

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse {

        /*
         * =====================================================================
         * TOOL RESULTS
         * =====================================================================
         *
         * Tool output is authoritative.
         * Do not reinterpret it locally.
         */

        if (
            messages.lastOrNull()?.role == "tool"
        ) {

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

        /*
         * =====================================================================
         * CURRENT USER MESSAGE
         * =====================================================================
         */

        val original =
            messages
                .lastOrNull {
                    it.role == "user"
                }
                ?.content
                ?.trim()
                ?: ""

        if (original.isBlank()) {

            return AiResponse(
                message = "What do you need?"
            )
        }

        val text =
            normalize(original)

        val isQuestion =
            isQuestion(
                text = text
            )

        /*
         * =====================================================================
         * BASIC CONVERSATION
         * =====================================================================
         *
         * Handle obvious conversational messages before domain routing.
         */

        if (
            greetingWord.containsMatchIn(text) &&
            !containsDomainIntent(text)
        ) {

            return AiResponse(
                message = "FRIDAY online. What do you need?"
            )
        }

        if (
            thanksWord.containsMatchIn(text) &&
            !containsDomainIntent(text)
        ) {

            return AiResponse(
                message = "You're welcome."
            )
        }

        if (
            goodbyeWord.containsMatchIn(text) &&
            !containsDomainIntent(text)
        ) {

            return AiResponse(
                message = "Goodbye."
            )
        }

        if (
            identityQuestion.containsMatchIn(text)
        ) {

            return AiResponse(
                message =
                    "I'm FRIDAY, your personal assistant."
            )
        }

        if (
            capabilityQuestion.containsMatchIn(text)
        ) {

            return AiResponse(
                message =
                    "I can manage meals, water, nutrition, TODOs, goals, " +
                            "reminders, and alarms."
            )
        }

        /*
         * =====================================================================
         * ALARMS
         * =====================================================================
         *
         * Alarm gets priority over reminders because phrases such as
         * "wake me at 7" are clearly alarm intent.
         */

        if (
            alarmWord.containsMatchIn(text)
        ) {

            val alarmCall =
                alarmParser.parse(
                    original = original,
                    text = text
                )

            if (alarmCall != null) {

                return AiResponse(
                    message = "",
                    toolCalls = listOf(alarmCall)
                )
            }

            if (
                Regex(
                    "\\b(delete|remove|cancel|disable|turn off|stop)\\b"
                ).containsMatchIn(text)
            ) {

                return AiResponse(
                    message =
                        "I can set alarms through the phone's Clock, " +
                                "but Android doesn't give FRIDAY a public way " +
                                "to remove that Clock alarm."
                )
            }

            return AiResponse(
                message =
                    "Tell me the alarm time, for example " +
                            "\"set an alarm for 7:30 AM\" or " +
                            "\"wake me in 10 minutes\"."
            )
        }

        /*
         * =====================================================================
         * REMINDERS
         * =====================================================================
         */

        if (
            reminderParser.isReminderRequest(text)
        ) {

            val reminderCall =
                reminderParser.parse(
                    original = original,
                    text = text,
                    isQuestion = isQuestion
                )

            if (reminderCall != null) {

                return AiResponse(
                    message = "",
                    toolCalls = listOf(reminderCall)
                )
            }

            if (
                reminderParser.needsTime(
                    text = text,
                    isQuestion = isQuestion
                )
            ) {

                return AiResponse(
                    message =
                        "What time should I remind you?"
                )
            }

            /*
             * The message clearly mentions reminders but does not
             * contain enough information for the local parser.
             *
             * Do not fall through into nutrition/task handling.
             */
            if (
                reminderWord.containsMatchIn(text)
            ) {

                return AiResponse(
                    message =
                        "Tell me what you want me to remind you about " +
                                "and when."
                )
            }
        }

        /*
         * =====================================================================
         * TODO + GOALS
         * =====================================================================
         */

        val taskCall =
            taskParser.parse(
                original = original,
                text = text,
                isQuestion = isQuestion
            )

        if (taskCall != null) {

            return AiResponse(
                message = "",
                toolCalls = listOf(taskCall)
            )
        }

        /*
         * =====================================================================
         * NUTRITION
         * =====================================================================
         *
         * The parser decides whether this is:
         *
         * - log_meal
         * - log_water
         * - get_today_nutrition
         * - get_week_nutrition
         */

        val nutritionCalls =
            nutritionParser.parse(
                original = original,
                text = text,
                isQuestion = isQuestion
            )

        if (nutritionCalls != null) {

            return AiResponse(
                message = "",
                toolCalls = nutritionCalls
            )
        }

        /*
         * =====================================================================
         * DOMAIN-SPECIFIC CLARIFICATION
         * =====================================================================
         *
         * If the user clearly mentions a domain but the parser could not
         * understand the requested action, ask instead of silently ignoring it.
         */

        if (
            taskWord.containsMatchIn(text)
        ) {

            return AiResponse(
                message =
                    "Tell me what you want to add, complete, delete, " +
                            "or see."
            )
        }

        if (
            nutritionWord.containsMatchIn(text) &&
            isQuestion
        ) {

            return AiResponse(
                message =
                    "I couldn't determine which nutrition information " +
                            "you want."
            )
        }

        /*
         * =====================================================================
         * GENERAL CONVERSATION
         * =====================================================================
         */

        val reply =
            when {

                text.contains("health") ->
                    "Health data isn't connected to the offline assistant yet."

                text.contains("screen time") ->
                    "Screen time is on the Tasks tab. " +
                            "I can't answer questions about it yet."

                isQuestion ->
                    "I don't have enough information to answer that offline."

                else ->
                    "I'm in offline mode. I can manage meals, water, " +
                            "nutrition, TODOs, goals, reminders, and alarms."
            }

        return AiResponse(
            message = reply
        )
    }

    /*
     * =========================================================================
     * QUESTION DETECTION
     * =========================================================================
     */

    private fun isQuestion(
        text: String
    ): Boolean {

        if (
            text.endsWith("?")
        ) {
            return true
        }

        if (
            questionStart.containsMatchIn(text)
        ) {
            return true
        }

        /*
         * Catch natural questions without requiring '?'.
         *
         * Example:
         * "tell me what I ate today"
         * "can you show my tasks"
         */

        if (
            conversationalQuestion.containsMatchIn(text) &&
            Regex(
                "\\b(tell|show|give|list|check|know|see)\\b"
            ).containsMatchIn(text)
        ) {
            return true
        }

        return false
    }

    /*
     * =========================================================================
     * DOMAIN DETECTION
     * =========================================================================
     */

    private fun containsDomainIntent(
        text: String
    ): Boolean {

        return alarmWord.containsMatchIn(text) ||
                reminderWord.containsMatchIn(text) ||
                taskWord.containsMatchIn(text) ||
                nutritionWord.containsMatchIn(text)
    }
}
