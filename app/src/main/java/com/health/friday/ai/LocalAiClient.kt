package com.health.friday.ai

private val questionStart =
    Regex("^(what|how|did|do|does|is|are|can|should|have|has|why|when|which)\\b")

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
    Regex("\\b(?:a|an|one)\\s+(?=(?:glass|glasses|bottle|bottles|cup|cups|litre|liter)\\b)")

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
                    .takeLastWhile { it.role == "tool" }
                    .joinToString("\n\n") { it.content }

            return AiResponse(message = results)
        }

        val original =
            messages
                .lastOrNull { it.role == "user" }
                ?.content
                ?.trim()
                ?: ""

        val text =
            original.lowercase()

        val isQuestion =
            text.endsWith("?") || questionStart.containsMatchIn(text)

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

                val args = mutableMapOf("amount_ml" to waterMl.toString())

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
                        (mealWord.containsMatchIn(text) &&
                                Regex("\\d").containsMatchIn(text))

            if (looksLikeMeal) {

                val args = mutableMapOf("text" to original)

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
                    AiToolCall(name = "get_week_nutrition")
                )
            )
        }

        if (nutritionWord.containsMatchIn(text)) {
            return AiResponse(
                message = "",
                toolCalls = listOf(
                    AiToolCall(name = "get_today_nutrition")
                )
            )
        }

        val reply =
            when {
                greetingWord.containsMatchIn(text) ->
                    "FRIDAY online. What do you need?"

                text.contains("who are you") ->
                    "I'm FRIDAY. Your personal assistant."

                Regex("\\bhealth\\b").containsMatchIn(text) ->
                    "Health data isn't connected yet."

                text.contains("screen time") ->
                    "Screen time is on the Tasks tab. I can't answer questions about it yet."

                else ->
                    "I'm in offline mode, so I only understand a few things. " +
                            "Tell me what you ate (\"I ate 2 eggs and 2 slices of toast\"), " +
                            "what you drank (\"drank 500 ml water\"), or ask what you've had today. " +
                            "A full AI isn't connected yet."
            }

        return AiResponse(message = reply)
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

        val ago = daysAgo.find(text)

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
            articleBeforeUnit.replace(text, "1 ")

        val match =
            waterAmount.find(prepared)
                ?: return null

        val amount =
            match.groupValues[1].toDoubleOrNull()
                ?: return null

        val unit =
            match.groupValues[2]

        val ml =
            when {
                unit == "ml" || unit.startsWith("milli") -> amount
                unit == "l" || unit.startsWith("lit") -> amount * 1000
                unit.startsWith("glass") -> amount * 250
                unit.startsWith("bottle") -> amount * 500
                unit.startsWith("cup") -> amount * 250
                else -> return null
            }

        val rounded =
            Math.round(ml).toInt()

        return if (rounded > 0) rounded else null
    }
}