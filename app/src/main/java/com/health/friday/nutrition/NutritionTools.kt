
package com.health.friday.nutrition

import com.health.friday.actions.ActionInverse
import com.health.friday.actions.ActionRecorder
import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.util.DateParser
import com.health.friday.util.DayRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val DATE_HELP =
    "Use today, yesterday, \"3 days ago\" or YYYY-MM-DD."

class LogWaterTool(
    private val waterRepository: WaterRepository,
    private val actionRecorder: ActionRecorder
) : AiTool {

    override val name = "log_water"

    override val description =
        "Logs water the user drank."

    override val parameters =
        listOf(
            AiToolParameter(
                name = "amount_ml",
                description = "Amount in millilitres as a whole number, for example '500'.",
                required = false
            ),
            AiToolParameter(
                name = "date",
                description = "Only if the day is not today: 'yesterday', '3 days ago' or YYYY-MM-DD."
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {
        val rawStr = (arguments["amount_ml"] ?: arguments["ml"] ?: arguments["amount"])?.trim().orEmpty()
        val digitsOnly = rawStr.filter { it.isDigit() || it == '.' }
        val amountMl = digitsOnly.toDoubleOrNull()?.roundToInt()

        if (amountMl == null || amountMl < 1 || amountMl > 5000) {
            return "Nothing logged. I need a water amount between 1 and 5000 ml."
        }

        val dateArg =
            arguments["date"]?.trim().orEmpty()

        val dayStart =
            DateParser.parseDayStart(dateArg)
                ?: return "Nothing logged. I didn't understand the date " +
                        "\"$dateArg\", or it is in the future. $DATE_HELP"

        val isToday =
            dayStart == DayRange.today().first

        if (isToday) {
            val waterId = waterRepository.addWater(amountMl)

            actionRecorder.setInverse(
                summary = "Logged $amountMl ml of water",
                inverseTool = ActionInverse.DELETE_WATER,
                inversePayloadJson = ActionInverse.waterPayload(waterId)
            )

            val total =
                waterRepository.getTodayTotalMl()

            return "Logged $amountMl ml of water. " +
                    "Today so far: $total of ${NutritionGoals.WATER_ML} ml."
        }

        val pastWaterId = waterRepository.addWater(
            amountMl = amountMl,
            timestamp = DateParser.timestampOnDay(dayStart, 12)
        )

        actionRecorder.setInverse(
            summary = "Logged $amountMl ml of water for a past day",
            inverseTool = ActionInverse.DELETE_WATER,
            inversePayloadJson = ActionInverse.waterPayload(pastWaterId)
        )

        val dayFormat =
            SimpleDateFormat("EEE dd MMM", Locale.getDefault())

        return "Logged $amountMl ml of water for " +
                "${dayFormat.format(Date(dayStart))}."
    }
}

class GetTodayNutritionTool(
    private val repository: NutritionRepository,
    private val waterRepository: WaterRepository
) : AiTool {

    override val name = "get_today_nutrition"

    override val description =
        "Returns what the user has eaten and drunk today: calories, protein, " +
                "carbs, fat, water, and the list of meals. Takes no arguments."

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {
        val summary =
            repository.getTodaySummary()

        val waterMl =
            waterRepository.getTodayTotalMl()

        return buildString {
            append(
                "Today: ${summary.calories} of ${NutritionGoals.CALORIES} kcal. " +
                        "Protein ${summary.protein.roundToInt()}g, " +
                        "carbs ${summary.carbohydrates.roundToInt()}g, " +
                        "fat ${summary.fat.roundToInt()}g.\n"
            )

            append(
                "Water: $waterMl of ${NutritionGoals.WATER_ML} ml.\n"
            )

            if (summary.meals.isEmpty()) {
                append("No meals logged today.")
            } else {
                append("Meals:")

                for (meal in summary.meals) {
                    append(
                        "\n• ${meal.mealType}: ${meal.name} " +
                                "(${meal.calories} kcal)"
                    )
                }
            }
        }
    }
}
