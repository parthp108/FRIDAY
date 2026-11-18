package com.health.friday.nutrition

import com.health.friday.actions.ActionInverse
import com.health.friday.actions.ActionRecorder
import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import java.util.Locale

class CorrectMealTool(
    private val nutritionRepository: NutritionRepository,
    private val actionRecorder: ActionRecorder
) : AiTool {

    override val name = "correct_meal"

    override val description =
        "Fixes the calories of a meal that was already logged, when the user states " +
                "the real value, for example 'that was actually 600 calories' or " +
                "'the biryani was more like 700 kcal'. Protein, carbs and fat are scaled " +
                "to match, and FRIDAY learns to adjust future entries of that food. " +
                "Without a food name it corrects the most recent meal from the last two days."

    override val parameters = listOf(
        AiToolParameter(
            name = "kcal",
            description = "The real total kilocalories for that meal, as a plain number.",
            required = true
        ),
        AiToolParameter(
            name = "food",
            description = "Part of the meal name, e.g. 'biryani'. Leave out to correct the most recent meal."
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val kcal = Regex("\\d+").find(arguments["kcal"].orEmpty())
            ?.value?.toIntOrNull()
            ?: return "Nothing changed. kcal must be a plain number."

        if (kcal !in 1..5000) {
            return "Nothing changed. $kcal kcal is outside the 1 to 5000 range for one meal."
        }

        return when (val outcome = nutritionRepository.correctMealCalories(arguments["food"], kcal)) {

            CorrectionOutcome.NotFound ->
                "Nothing changed. I couldn't find a matching meal from today or yesterday."

            is CorrectionOutcome.Rejected ->
                "Nothing changed. ${outcome.reason}"

            is CorrectionOutcome.Done -> {
                val c = outcome.correction

                actionRecorder.setInverse(
                    summary = "Corrected ${c.mealName} from ${c.oldCalories} to ${c.newCalories} kcal",
                    inverseTool = ActionInverse.RESTORE_MEAL,
                    inversePayloadJson = ActionInverse.restoreMealPayload(
                        mealId = c.mealId,
                        calories = c.oldCalories,
                        protein = c.oldProtein,
                        carbs = c.oldCarbs,
                        fat = c.oldFat,
                        nutrientsJson = c.oldNutrientsJson,
                        calibrationKey = c.calibrationKey,
                        hadPreviousCalibration = c.previousCalibration != null,
                        previousBias = c.previousCalibration?.biasMultiplier ?: 1.0,
                        previousCount = c.previousCalibration?.correctionCount ?: 0,
                        previousAt = c.previousCalibration?.lastCorrectedAt ?: 0L
                    )
                )

                "Corrected ${c.mealName} from ${c.oldCalories} to ${c.newCalories} kcal " +
                        "(protein, carbs and fat scaled to match). Future entries of this food " +
                        "will be scaled x${String.format(Locale.US, "%.2f", c.newBias)} " +
                        "(${c.correctionCount} correction${if (c.correctionCount == 1) "" else "s"} so far)."
            }
        }
    }
}
