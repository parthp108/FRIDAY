package com.health.friday.actions

import com.health.friday.data.local.ActionLogDao
import com.health.friday.data.local.ActionLogEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The inverse operations FRIDAY knows how to perform, and the JSON payload
 * each one needs. UndoLastActionTool reads these back.
 */
object ActionInverse {

    const val DELETE_MEALS = "delete_meals"
    const val DELETE_WATER = "delete_water"
    const val RESTORE_MEAL = "restore_meal"

    fun mealsPayload(mealIds: List<Long>): String =
        JSONObject().put("mealIds", JSONArray(mealIds)).toString()

    fun waterPayload(waterId: Long): String =
        JSONObject().put("waterId", waterId).toString()

    /** Everything needed to put a corrected meal and its calibration back. */
    fun restoreMealPayload(
        mealId: Long,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        nutrientsJson: String,
        calibrationKey: String,
        hadPreviousCalibration: Boolean,
        previousBias: Double,
        previousCount: Int,
        previousAt: Long
    ): String =
        JSONObject()
            .put("mealId", mealId)
            .put("calories", calories)
            .put("protein", protein)
            .put("carbs", carbs)
            .put("fat", fat)
            .put("nutrientsJson", nutrientsJson)
            .put("calibrationKey", calibrationKey)
            .put("hadPrevious", hadPreviousCalibration)
            .put("prevBias", previousBias)
            .put("prevCount", previousCount)
            .put("prevAt", previousAt)
            .toString()
}

/**
 * Records undoable tool actions into the action_log table.
 *
 * How it works:
 *  1. AiOrchestrator runs every tool through execute(...).
 *  2. A tool that changes data calls setInverse(...) to say how to reverse it.
 *  3. After the tool returns, execute(...) writes ONE row containing the tool
 *     name, the exact arguments, a summary, and the inverse.
 *
 * Tools that do not call setInverse are simply not recorded.
 * Recording failures never break the action itself.
 */
class ActionRecorder(
    private val actionLogDao: ActionLogDao
) {

    private data class PendingInverse(
        val summary: String,
        val inverseTool: String,
        val inversePayloadJson: String
    )

    private val mutex = Mutex()
    private var pending: PendingInverse? = null

    /** Called by a tool, while it is executing, after it has changed data. */
    fun setInverse(
        summary: String,
        inverseTool: String,
        inversePayloadJson: String
    ) {
        pending = PendingInverse(summary, inverseTool, inversePayloadJson)
    }

    suspend fun execute(
        toolName: String,
        arguments: Map<String, String>,
        block: suspend () -> String
    ): String = mutex.withLock {

        pending = null

        val result = try {
            block()
        } catch (e: Throwable) {
            pending = null
            throw e
        }

        val inverse = pending
        pending = null

        if (inverse != null) {
            record(toolName, arguments, inverse)
        }

        result
    }

    private suspend fun record(
        toolName: String,
        arguments: Map<String, String>,
        inverse: PendingInverse
    ) {
        try {
            // Finish the write even if the chat screen is closed mid-request.
            withContext(NonCancellable) {
                val argumentsJson = JSONObject(arguments).toString()

                actionLogDao.insertAction(
                    ActionLogEntity(
                        toolName = toolName,
                        description = "$toolName $argumentsJson -> ${inverse.summary}".take(600),
                        inverseTool = inverse.inverseTool,
                        inversePayloadJson = inverse.inversePayloadJson
                    )
                )

                actionLogDao.deleteOlderThan(
                    System.currentTimeMillis() - RETENTION_MS
                )
            }
        } catch (_: Exception) {
            // Losing the undo record must never undo or hide the real action.
        }
    }

    private companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
