package com.health.friday.nutrition

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.util.DateParser
import com.health.friday.util.DayRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun typicalHour(
    mealType: String
): Int {

    return when (mealType) {
        "Breakfast" -> 8
        "Lunch" -> 13
        "Snack" -> 17
        "Dinner" -> 20
        else -> 12
    }
}

class LogEstimatedMealTool(
    private val repository: NutritionRepository
) : AiTool {

    override val name = "log_estimated_meal"

    override val description =
        "Logs one food or dish with nutrition values YOU estimate, for anything " +
                "log_meal does not recognise. log_meal only knows: " +
                "${FoodParser().supportedFoodNames.joinToString(", ")}. " +
                "Call once per dish. Values are for the whole portion the user " +
                "ate. It is saved marked as estimated."

    override val parameters =
        listOf(
            AiToolParameter(
                name = "name",
                description = "Dish and portion, e.g. 'Chicken biryani (1 bowl, about 350 g)'.",
                required = true
            ),
            AiToolParameter(
                name = "calories",
                description = "Estimated kilocalories for the whole portion, e.g. '620'.",
                required = true
            ),
            AiToolParameter(
                name = "protein_g",
                description = "Estimated grams of protein for the whole portion.",
                required = true
            ),
            AiToolParameter(
                name = "carbs_g",
                description = "Estimated grams of carbohydrate for the whole portion.",
                required = true
            ),
            AiToolParameter(
                name = "fat_g",
                description = "Estimated grams of fat for the whole portion.",
                required = true
            ),
            AiToolParameter(
                name = "meal_type",
                description = "Breakfast, Lunch, Dinner or Snack. Only if the user said or clearly implied it."
            ),
            AiToolParameter(
                name = "date",
                description = "Only if the day is not today: 'yesterday', '3 days ago' or YYYY-MM-DD."
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val dishName =
            arguments["name"]?.trim().orEmpty().take(60)

        if (dishName.isEmpty()) {
            return "Nothing logged. No food name was given."
        }

        val calories =
            arguments["calories"]?.trim()?.toDoubleOrNull()?.roundToInt()

        val protein =
            arguments["protein_g"]?.trim()?.toDoubleOrNull()

        val carbs =
            arguments["carbs_g"]?.trim()?.toDoubleOrNull()

        val fat =
            arguments["fat_g"]?.trim()?.toDoubleOrNull()

        if (calories == null || protein == null || carbs == null || fat == null) {
            return "Nothing logged. calories, protein_g, carbs_g and fat_g " +
                    "must all be plain numbers."
        }

        if (calories < 1 || calories > 4000 ||
            protein < 0.0 || protein > 400.0 ||
            carbs < 0.0 || carbs > 600.0 ||
            fat < 0.0 || fat > 300.0
        ) {
            return "Nothing logged. Those values look unrealistic for one " +
                    "meal. Re-estimate and call again."
        }

        val impliedCalories = 4 * protein + 4 * carbs + 9 * fat

        if (calories >= 50 &&
            abs(impliedCalories - calories) > 0.35 * calories
        ) {
            return "Nothing logged. The macros add up to about " +
                    "${impliedCalories.roundToInt()} kcal but calories say " +
                    "$calories. Re-estimate with consistent numbers and call again."
        }

        val dateArg =
            arguments["date"]?.trim().orEmpty()

        val dayStart =
            DateParser.parseDayStart(dateArg)
                ?: return "Nothing logged. I didn't understand the date " +
                        "\"$dateArg\", or it is in the future."

        val isToday =
            dayStart == DayRange.today().first

        val mealType =
            when (arguments["meal_type"]?.trim()?.lowercase()) {
                "breakfast" -> "Breakfast"
                "lunch" -> "Lunch"
                "dinner" -> "Dinner"
                "snack" -> "Snack"
                else -> MealType.resolve("")
            }

        val timestamp =
            if (isToday) {
                System.currentTimeMillis()
            } else {
                DateParser.timestampOnDay(dayStart, typicalHour(mealType))
            }

        val displayName =
            dishName.replaceFirstChar { it.uppercase() }

        repository.logEstimated(
            name = displayName,
            mealType = mealType,
            calories = calories,
            protein = protein,
            carbohydrates = carbs,
            fat = fat,
            timestamp = timestamp
        )

        val dayFormat =
            SimpleDateFormat("EEE dd MMM", Locale.getDefault())

        val dayLabel =
            if (isToday) "" else " for ${dayFormat.format(Date(dayStart))}"

        val dayTotal =
            if (isToday) {
                repository.getTodaySummary().calories
            } else {
                repository
                    .getMealsBetween(
                        dayStart,
                        DayRange.shiftDays(dayStart, 1)
                    )
                    .sumOf { it.calories }
            }

        val totalLabel = if (isToday) "Today so far" else "That day"

        return "Logged as $mealType$dayLabel (ESTIMATE, not measured): " +
                "$displayName: $calories kcal " +
                "(P ${protein.roundToInt()}g, " +
                "C ${carbs.roundToInt()}g, " +
                "F ${fat.roundToInt()}g). " +
                "$totalLabel: $dayTotal of ${NutritionGoals.CALORIES} kcal."
    }
}