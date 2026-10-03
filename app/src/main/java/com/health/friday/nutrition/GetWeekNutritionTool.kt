package com.health.friday.nutrition

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class GetWeekNutritionTool(
    private val repository: NutritionRepository,
    private val waterRepository: WaterRepository
) : AiTool {

    override val name = "get_week_nutrition"

    override val description =
        "Returns per-day calories, macros and water for the last N days " +
                "(default 7). Days with no logged meals are marked unknown, " +
                "not zero."

    override val parameters =
        listOf(
            AiToolParameter(
                name = "days",
                description = "Number of days to look back, 1 to 31. Default 7."
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val days =
            arguments["days"]
                ?.trim()
                ?.toDoubleOrNull()
                ?.toInt()
                ?.coerceIn(1, 31)
                ?: 7

        val totals =
            NutritionReportBuilder(repository, waterRepository)
                .lastDays(days)

        val format =
            SimpleDateFormat("EEE dd MMM", Locale.getDefault())

        val logged =
            totals.filter { it.mealCount > 0 }

        return buildString {

            append(
                "Last $days days (goals: ${NutritionGoals.CALORIES} kcal, " +
                        "${NutritionGoals.WATER_ML} ml water):\n"
            )

            for (day in totals) {

                val label = format.format(Date(day.dayStart))

                if (day.mealCount == 0) {

                    append(
                        "• $label: no meals logged, water ${day.waterMl} ml\n"
                    )

                } else {

                    append(
                        "• $label: ${day.calories} kcal, " +
                                "P ${day.protein.roundToInt()}g, " +
                                "C ${day.carbohydrates.roundToInt()}g, " +
                                "F ${day.fat.roundToInt()}g, " +
                                "water ${day.waterMl} ml\n"
                    )
                }
            }

            append(
                "Days with meals logged: ${logged.size} of $days. " +
                        "Days with no meals logged are unknown, not zero."
            )

            if (logged.isNotEmpty()) {

                val average =
                    logged.sumOf { it.calories } / logged.size

                val highest =
                    logged.maxBy { it.calories }

                val over =
                    logged.count { it.calories > NutritionGoals.CALORIES }

                append(
                    "\nAverage on logged days: $average kcal. " +
                            "Highest: ${format.format(Date(highest.dayStart))} " +
                            "(${highest.calories} kcal). " +
                            "Days over the calorie goal: $over."
                )
            }
        }
    }
}