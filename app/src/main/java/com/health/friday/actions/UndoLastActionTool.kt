package com.health.friday.actions

import com.health.friday.ai.AiTool
import com.health.friday.data.local.ActionLogDao
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.WaterRepository
import com.health.friday.nutrition.calibration.FoodCalibrationEntity
import org.json.JSONObject

class UndoLastActionTool(
    private val actionLogDao: ActionLogDao,
    private val nutritionRepository: NutritionRepository,
    private val waterRepository: WaterRepository
) : AiTool {

    override val name = "undo_last_action"

    override val description =
        "Undoes the most recent food or water entry FRIDAY saved, or the most recent " +
                "meal correction. Use when the " +
                "user says undo, undo that, or that was wrong. Takes no arguments. " +
                "Only actions from the last 3 hours can be undone."

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val action = actionLogDao.getLastUndoableAction()
            ?: return "Nothing to undo."

        val ageMs = System.currentTimeMillis() - action.timestamp

        if (ageMs > MAX_UNDO_AGE_MS) {
            val minutes = ageMs / 60_000
            return "The last undoable action was $minutes minutes ago, which is too " +
                    "old to undo automatically. Nothing was changed."
        }

        val payload = try {
            JSONObject(action.inversePayloadJson)
        } catch (_: Exception) {
            return "Couldn't read the undo data for the last action. Nothing was changed."
        }

        val outcome: String = when (action.inverseTool) {

            ActionInverse.DELETE_MEALS -> {
                val array = payload.optJSONArray("mealIds")
                val ids = mutableListOf<Long>()

                if (array != null) {
                    for (i in 0 until array.length()) {
                        ids.add(array.getLong(i))
                    }
                }

                val removed = nutritionRepository.deleteMealsByIds(ids)

                if (removed == 0) {
                    "Those meal entries were already removed."
                } else {
                    "Removed $removed meal entr${if (removed == 1) "y" else "ies"}."
                }
            }

            ActionInverse.DELETE_WATER -> {
                val removed =
                    waterRepository.deleteWaterById(payload.optLong("waterId", -1L))

                if (removed) {
                    "Removed the water entry."
                } else {
                    "That water entry was already removed."
                }
            }

            ActionInverse.RESTORE_MEAL -> {
                val key = payload.optString("calibrationKey")

                val previous =
                    if (payload.optBoolean("hadPrevious", false)) {
                        FoodCalibrationEntity(
                            canonicalName = key,
                            biasMultiplier = payload.optDouble("prevBias", 1.0),
                            correctionCount = payload.optInt("prevCount", 0),
                            lastCorrectedAt = payload.optLong("prevAt", 0L)
                        )
                    } else {
                        null
                    }

                val mealName = nutritionRepository.restoreCorrectedMeal(
                    mealId = payload.optLong("mealId", -1L),
                    calories = payload.optInt("calories", 0),
                    protein = payload.optDouble("protein", 0.0),
                    carbs = payload.optDouble("carbs", 0.0),
                    fat = payload.optDouble("fat", 0.0),
                    nutrientsJson = payload.optString("nutrientsJson", "[]"),
                    calibrationKey = key,
                    previousCalibration = previous
                )

                if (mealName == null) {
                    "That meal no longer exists. The learned adjustment was still reverted."
                } else {
                    "Restored $mealName to its earlier calories and reverted the learned adjustment."
                }
            }

            else -> return "Unknown undo type '${action.inverseTool}'. Nothing was changed."
        }

        actionLogDao.deleteAction(action.id)

        return "Undone. $outcome"
    }

    private companion object {
        const val MAX_UNDO_AGE_MS = 3L * 60 * 60 * 1000
    }
}
