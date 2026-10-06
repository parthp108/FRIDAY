package com.health.friday.ai.parsers

import com.health.friday.ai.AiToolCall
import java.util.Locale

class LocalNutritionParser {

    /*
     * ========================================================================
     * MEALS
     * ========================================================================
     */

    private val eatVerb =
        Regex(
            "\\b(ate|eaten|eating|eat|had|have|having|consumed|consume)\\b"
        )

    private val mealWord =
        Regex(
            "\\b(breakfast|lunch|dinner|supper|snack|meal|food)\\b"
        )

    /*
     * ========================================================================
     * WATER
     * ========================================================================
     */

    private val waterWord =
        Regex(
            "\\b(water|hydration|hydrated|drink|drank|drinking)\\b"
        )

    private val waterAmount =
        Regex(
            "(\\d+(?:\\.\\d+)?)\\s*" +
                    "(ml|millilit(?:er|re)s?|l|lit(?:er|re)s?|glass(?:es)?|" +
                    "bottles?|cups?)\\b"
        )

    private val articleBeforeUnit =
        Regex(
            "\\b(?:a|an|one)\\s+" +
                    "(?=(?:glass|glasses|bottle|bottles|cup|cups|litre|liter)\\b)"
        )

    /*
     * ========================================================================
     * NUTRITION QUESTIONS
     * ========================================================================
     */

    private val nutritionWord =
        Regex(
            "\\b(eat|ate|eaten|had|have|calories|calorie|kcal|protein|proteins|" +
                    "carbs|carb|fat|fats|macros|macro|nutrition|food|meals|meal|" +
                    "water|hydration|overeat|overeaten)\\b"
        )

    private val weekWord =
        Regex(
            "\\b(week|weekly|7 days|seven days)\\b"
        )

    /*
     * ========================================================================
     * DATES
     * ========================================================================
     */

    private val dayBeforeYesterday =
        Regex(
            "\\bday before yesterday\\b"
        )

    private val yesterdayWord =
        Regex(
            "\\byesterday\\b"
        )

    private val daysAgo =
        Regex(
            "\\b(\\d+)\\s+days?\\s+ago\\b"
        )

    /*
     * ========================================================================
     * PUBLIC PARSING
     * ========================================================================
     */

    fun parse(
        original: String,
        text: String,
        isQuestion: Boolean
    ): List<AiToolCall>? {

        if (isQuestion) {
            return parseNutritionQuestion(text)
        }

        val toolCalls =
            mutableListOf<AiToolCall>()

        val date =
            extractDate(text)

        /*
         * ------------------------------------------------------------
         * WATER
         * ------------------------------------------------------------
         */

        val waterMl =
            if (waterWord.containsMatchIn(text)) {
                extractWaterMl(text)
            } else {
                null
            }

        if (waterMl != null) {

            val arguments =
                mutableMapOf(
                    "amount_ml" to waterMl.toString()
                )

            if (date != null) {
                arguments["date"] = date
            }

            toolCalls.add(
                AiToolCall(
                    name = "log_water",
                    arguments = arguments
                )
            )
        }

        /*
         * ------------------------------------------------------------
         * MEAL
         * ------------------------------------------------------------
         */

        val looksLikeMeal =
            eatVerb.containsMatchIn(text) ||
                    (
                            mealWord.containsMatchIn(text) &&
                                    Regex("\\d")
                                        .containsMatchIn(text)
                            )

        if (looksLikeMeal) {

            val arguments =
                mutableMapOf(
                    "text" to original
                )

            if (date != null) {
                arguments["date"] = date
            }

            toolCalls.add(
                AiToolCall(
                    name = "log_meal",
                    arguments = arguments
                )
            )
        }

        return if (toolCalls.isNotEmpty()) {
            toolCalls
        } else {
            null
        }
    }

    fun isNutritionRequest(
        text: String
    ): Boolean {

        return nutritionWord.containsMatchIn(text)
    }

    /*
     * ========================================================================
     * NUTRITION QUESTIONS
     * ========================================================================
     */

    private fun parseNutritionQuestion(
        text: String
    ): List<AiToolCall>? {

        if (weekWord.containsMatchIn(text)) {

            return listOf(
                AiToolCall(
                    name = "get_week_nutrition"
                )
            )
        }

        if (nutritionWord.containsMatchIn(text)) {

            return listOf(
                AiToolCall(
                    name = "get_today_nutrition"
                )
            )
        }

        return null
    }

    /*
     * ========================================================================
     * DATE
     * ========================================================================
     */

    fun extractDate(
        text: String
    ): String? {

        if (
            dayBeforeYesterday.containsMatchIn(text)
        ) {
            return "day before yesterday"
        }

        if (
            yesterdayWord.containsMatchIn(text)
        ) {
            return "yesterday"
        }

        val ago =
            daysAgo.find(text)

        if (ago != null) {
            return "${ago.groupValues[1]} days ago"
        }

        return null
    }

    /*
     * ========================================================================
     * WATER AMOUNT
     * ========================================================================
     */

    fun extractWaterMl(
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
                .lowercase(Locale.ROOT)

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
            ml
                .toLong()
                .coerceAtMost(
                    Int.MAX_VALUE.toLong()
                )
                .toInt()

        return if (rounded > 0) {
            rounded
        } else {
            null
        }
    }
}