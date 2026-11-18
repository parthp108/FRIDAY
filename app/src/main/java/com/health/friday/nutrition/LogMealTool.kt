
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
import kotlin.math.abs
import kotlin.math.roundToInt

class LogMealTool(
    private val nutritionRepository: NutritionRepository,
    private val actionRecorder: ActionRecorder,
    private val foodParser: FoodParser = FoodParser()
) : AiTool {

    override val name: String = "log_meal"

    override val description: String =
        "Log foods the user has eaten using FRIDAY's local nutrition database. " +
                "Use the user's original food description as the text argument."

    override val parameters: List<AiToolParameter> = listOf(
        AiToolParameter(
            name = "text",
            description = "The food description, including quantities, such as 'I ate 2 eggs and 2 slices of toast'.",
            required = true
        ),
        AiToolParameter(
            name = "meal_type",
            description = "Optional meal type: breakfast, lunch, dinner, or snack."
        ),
        AiToolParameter(
            name = "date",
            description = "Only if the day is not today: 'yesterday', '3 days ago' or YYYY-MM-DD."
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"]?.trim().orEmpty()

        if (text.isBlank()) {
            return "No food description was provided. Nothing was logged."
        }

        val foods = foodParser.parse(text)

        if (foods.isEmpty()) {
            return "I couldn't identify any food items. Nothing was logged."
        }

        val mealType = MealType.resolve(
            arguments["meal_type"].orEmpty().ifEmpty { text },
            System.currentTimeMillis()
        )

        val dateArg = arguments["date"]?.trim().orEmpty()
        val dayStart = DateParser.parseDayStart(dateArg)
            ?: return "Nothing logged. I didn't understand the date \"$dateArg\", or it is in the future."

        val isToday = dayStart == DayRange.today().first
        val timestamp = if (isToday) {
            System.currentTimeMillis()
        } else {
            DateParser.timestampOnDay(
                dayStart,
                when (mealType) {
                    "Breakfast" -> 8
                    "Lunch" -> 13
                    "Snack" -> 17
                    "Dinner" -> 20
                    else -> 12
                }
            )
        }

        val result = nutritionRepository.logFoods(
            foods = foods,
            mealType = mealType,
            timestamp = timestamp
        )

        if (result.logged.isEmpty()) {
            val skippedNames = result.skipped.joinToString { food ->
                "${food.quantity} ${food.unit} ${food.name}"
            }

            return "No foods were logged because the local nutrition database " +
                    "couldn't provide usable nutrition data for the identified items." +
                    if (skippedNames.isNotBlank()) "\nUnmatched items: $skippedNames" else ""
        }

        val totalCalories = result.logged.sumOf {
            it.nutrition.calories
        }.roundToInt()

        val loggedLines = result.logged.joinToString("\n") { item ->
            "- ${item.displayName}: ${item.nutrition.calories.roundToInt()} kcal" +
                    if (abs(item.calibrationMultiplier - 1.0) > 0.02) {
                        " (adjusted x${String.format(Locale.US, "%.2f", item.calibrationMultiplier)} from your corrections)"
                    } else {
                        ""
                    }
        }

        val skippedLines = if (result.skipped.isNotEmpty()) {
            "\n\nNot logged because nutrition data was unavailable:\n" +
                    result.skipped.joinToString("\n") { food ->
                        "- ${food.quantity} ${food.unit} ${food.name}"
                    }
        } else {
            ""
        }

        val dayFormat = SimpleDateFormat("EEE dd MMM", Locale.getDefault())
        val dayLabel = if (isToday) "" else " for ${dayFormat.format(Date(dayStart))}"

        actionRecorder.setInverse(
            summary = "Logged ${result.logged.size} food item(s) for $mealType$dayLabel, $totalCalories kcal",
            inverseTool = ActionInverse.DELETE_MEALS,
            inversePayloadJson = ActionInverse.mealsPayload(
                result.logged.map { it.mealId }
            )
        )

        return "Logged ${result.logged.size} food item(s) for $mealType$dayLabel.\n" +
                "$loggedLines\n" +
                "Total logged calories: $totalCalories kcal" +
                skippedLines
    }
}
