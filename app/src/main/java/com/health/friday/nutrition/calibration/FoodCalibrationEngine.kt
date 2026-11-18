package com.health.friday.nutrition.calibration

import kotlin.math.pow

/**
 * Learns how far off FRIDAY's portion estimates are for each food, from the
 * corrections you give it, and scales future entries of that food.
 *
 * CONTRACT: recordCorrection must be given the calories the user was SHOWN
 * (the number that already includes any earlier adjustment). That makes the
 * bias converge on actual / raw estimate instead of overshooting.
 *
 * Update rule:  new = clamp(old * (actual / shown) ^ 0.3, 0.5, 2.0)
 * The 0.3 exponent means one correction moves the bias about a third of the
 * way, so a single typo cannot wreck a food.
 */
class FoodCalibrationEngine(private val dao: FoodCalibrationDao) {

    /** Multiplier to apply to a food's nutrition. 1.0 when nothing is learned yet. */
    suspend fun multiplierFor(foodName: String): Double =
        dao.getCalibration(keyFor(foodName))?.biasMultiplier ?: 1.0

    suspend fun stateFor(foodName: String): FoodCalibrationEntity? =
        dao.getCalibration(keyFor(foodName))

    /**
     * Records one correction and returns the calibration row as it was BEFORE,
     * (null if the food had none) so the change can be undone.
     */
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

    /** Puts a calibration back exactly as it was (null deletes it). Takes the key from keyFor(). */
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

        /**
         * "Toast × 2 slices" -> "Toast"
         * "Chicken biryani (1 bowl, about 350 g)" -> "Chicken biryani"
         */
        fun canonicalName(mealName: String): String =
            mealName.substringBefore(" × ").substringBefore(" (").trim()
    }
}
