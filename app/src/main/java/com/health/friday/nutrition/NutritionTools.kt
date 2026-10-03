package com.health.friday.nutrition

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.util.DateParser
import com.health.friday.util.DayRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val ignoredWords =
    setOf(
        "i", "ve", "ll", "ate", "eat", "eaten", "eating", "had", "have",
        "just", "for", "my", "some", "the", "of", "with", "then", "also",
        "today", "now", "earlier", "tonight", "morning", "evening",
        "afternoon", "breakfast", "lunch", "dinner", "supper", "snack",
        "snacks", "and", "an", "to", "at", "in", "on", "so", "plus",
        "after", "before", "was", "were", "it", "me", "log", "add",
        "please", "large", "slice", "slices", "cup", "cups", "glass",
        "glasses", "drank", "drink", "water", "ml", "yesterday", "day",
        "days", "ago", "last", "night"
    )

private fun leftoverWords(
    unknownText: String
): List<String> {

    return unknownText
        .split(Regex("[^a-z]+"))
        .filter { it.length > 1 && it !in ignoredWords }
        .distinct()
}

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

private const val DATE_HELP =
    "Use today, yesterday, \"3 days ago\" or YYYY-MM-DD."

class LogMealTool(
    private val repository: NutritionRepository,
    private val parser: FoodParser = FoodParser()
) : AiTool {

    override val name = "log_meal"

    override val description =
        "Logs food the user ate and saves it with calories and macros. " +
                "Only foods in the app's food list are recognised."

    override val parameters =
        listOf(
            AiToolParameter(
                name = "text",
                description = "What the user said they ate, for example " +
                        "'2 eggs and 2 slices of toast'.",
                required = true
            ),
            AiToolParameter(
                name = "meal_type",
                description = "Breakfast, Lunch, Dinner or Snack. Only if " +
                        "the user said or clearly implied it."
            ),
            AiToolParameter(
                name = "date",
                description = "Only if the day is not today: 'yesterday', " +
                        "'3 days ago' or YYYY-MM-DD."
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val text =
            arguments["text"]?.trim().orEmpty()

        if (text.isEmpty()) {
            return "Nothing logged. No food text was given."
        }

        val dateArg =
            arguments["date"]?.trim().orEmpty()

        val dayStart =
            DateParser.parseDayStart(dateArg)
                ?: return "Nothing logged. I didn't understand the date " +
                        "\"$dateArg\", or it is in the future. $DATE_HELP"

        val isToday =
            dayStart == DayRange.today().first

        val parsed =
            parser.parseResult(text)

        if (parsed.foods.isEmpty()) {
            return "Nothing logged. I didn't find a food with a quantity in " +
                    "\"$text\". I only know these foods right now: " +
                    "${parser.supportedFoodNames.joinToString(", ")}. " +
                    "Say it like \"2 eggs\"."
        }

        val mealType =
            arguments["meal_type"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: MealType.resolve(text)

        val timestamp =
            if (isToday) {
                System.currentTimeMillis()
            } else {
                DateParser.timestampOnDay(dayStart, typicalHour(mealType))
            }

        val result =
            repository.logFoods(
                foods = parsed.foods,
                mealType = mealType,
                timestamp = timestamp
            )

        if (result.logged.isEmpty()) {
            return "Nothing logged. I have no nutrition data for those foods yet."
        }

        val leftover =
            leftoverWords(parsed.unknownText)

        val dayFormat =
            SimpleDateFormat("EEE dd MMM", Locale.getDefault())

        val dayLabel =
            if (isToday) "" else " for ${dayFormat.format(Date(dayStart))}"

        return buildString {

            append("Logged as $mealType$dayLabel:\n")

            for (item in result.logged) {

                val n = item.nutrition

                append(
                    "• ${item.displayName}: ${n.calories.roundToInt()} kcal " +
                            "(P ${n.protein.roundToInt()}g, " +
                            "C ${n.carbohydrates.roundToInt()}g, " +
                            "F ${n.fat.roundToInt()}g)\n"
                )
            }

            if (leftover.isNotEmpty()) {
                append(
                    "Not logged (not in my food list): " +
                            "${leftover.joinToString(", ")}.\n"
                )
            }

            if (isToday) {

                val summary = repository.getTodaySummary()

                append(
                    "Today so far: ${summary.calories} of " +
                            "${NutritionGoals.CALORIES} kcal."
                )

            } else {

                val dayMeals =
                    repository.getMealsBetween(
                        dayStart,
                        DayRange.shiftDays(dayStart, 1)
                    )

                append(
                    "That day: ${dayMeals.sumOf { it.calories }} of " +
                            "${NutritionGoals.CALORIES} kcal."
                )
            }
        }
    }
}

class LogWaterTool(
    private val waterRepository: WaterRepository
) : AiTool {

    override val name = "log_water"

    override val description =
        "Logs water the user drank."

    override val parameters =
        listOf(
            AiToolParameter(
                name = "amount_ml",
                description = "Amount in millilitres as a whole number, " +
                        "for example '500'.",
                required = true
            ),
            AiToolParameter(
                name = "date",
                description = "Only if the day is not today: 'yesterday', " +
                        "'3 days ago' or YYYY-MM-DD."
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val amountMl =
            arguments["amount_ml"]
                ?.trim()
                ?.toDoubleOrNull()
                ?.roundToInt()

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

            waterRepository.addWater(amountMl)

            val total =
                waterRepository.getTodayTotalMl()

            return "Logged $amountMl ml of water. " +
                    "Today so far: $total of ${NutritionGoals.WATER_ML} ml."
        }

        waterRepository.addWater(
            amountMl = amountMl,
            timestamp = DateParser.timestampOnDay(dayStart, 12)
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