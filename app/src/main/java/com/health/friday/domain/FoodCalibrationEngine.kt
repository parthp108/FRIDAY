package com.health.friday.domain

import com.health.friday.data.local.FoodCalibrationDao
import com.health.friday.data.local.FoodCalibrationEntity
import kotlin.math.pow

class FoodCalibrationEngine(private val dao: FoodCalibrationDao) {

    suspend fun multiplierFor(foodName: String): Double =
        dao.getCalibration(keyFor(foodName))?.biasMultiplier ?: 1.0

    suspend fun stateFor(foodName: String): FoodCalibrationEntity? =
        dao.getCalibration(keyFor(foodName))

    suspend fun recordCorrection(
        foodName: String,
        shownKcal: Double,
        actualKcal: Double
    ): FoodCalibrationEntity? {
        val key = keyFor(foodName)
        val existing = dao.getCalibration(key)

        if (shownKcal <= 0 || actualKcal <= 0) return existing

        val currentBias = existing?.biasMultiplier ?: 1.0
        val count = existing?.correctionCount ?: 0

        val ratio = actualKcal / shownKcal
        val newBias = (currentBias * ratio.pow(LEARNING_RATE)).coerceIn(MIN_BIAS, MAX_BIAS)

        dao.insertOrUpdate(
            FoodCalibrationEntity(
                canonicalName = key,
                biasMultiplier = newBias,
                correctionCount = count + 1,
                lastCorrectedAt = System.currentTimeMillis()
            )
        )

        return existing
    }

    suspend fun restore(key: String, previous: FoodCalibrationEntity?) {
        if (previous == null) {
            dao.deleteCalibration(key)
        } else {
            dao.insertOrUpdate(previous)
        }
    }

    fun keyFor(foodName: String): String =
        canonicalName(foodName).lowercase().replace(Regex("[^a-z0-9]+"), "")

    companion object {
        private const val LEARNING_RATE = 0.3
        private const val MIN_BIAS = 0.5
        private const val MAX_BIAS = 2.0

        fun canonicalName(mealName: String): String =
            mealName.substringBefore(" × ").substringBefore(" (").trim()
    }
}
