package com.health.friday.nutrition.calibration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.pow

class FakeFoodCalibrationDao : FoodCalibrationDao {
    private val storage = mutableMapOf<String, FoodCalibrationEntity>()

    override suspend fun getCalibration(name: String): FoodCalibrationEntity? {
        return storage[name]
    }

    override suspend fun insertOrUpdate(calibration: FoodCalibrationEntity) {
        storage[calibration.canonicalName] = calibration
    }

    override suspend fun deleteCalibration(name: String) {
        storage.remove(name)
    }
}

class FoodCalibrationEngineTest {

    private val fakeDao = FakeFoodCalibrationDao()
    private val engine = FoodCalibrationEngine(fakeDao)

    @Test
    fun testCorrectionDampenedLearningRate() = runBlocking {
        // Initial state: no calibration stored for "oatmeal"
        assertEquals(1.0, engine.multiplierFor("oatmeal"), 0.001)

        // Correction: shown = 300 kcal, actual = 200 kcal
        // Expected formula: 1.0 * (200.0 / 300.0)^0.3 = 0.6667^0.3 ≈ 0.8851
        val previous = engine.recordCorrection("oatmeal", shownKcal = 300.0, actualKcal = 200.0)

        // Previous was null because nothing was saved before
        assertEquals(null, previous)

        val newState = engine.stateFor("oatmeal")
        assertNotNull(newState)

        val expectedBias = (200.0 / 300.0).pow(0.3) // ~0.8851
        assertEquals(expectedBias, newState!!.biasMultiplier, 0.005)
        assertEquals(1, newState.correctionCount)

        // Test multiplierFor returns the learned bias
        val multiplier = engine.multiplierFor("oatmeal")
        assertEquals(0.885, multiplier, 0.01)
    }

    @Test
    fun testCorrectionConvergence() = runBlocking {
        // First correction: 300 shown -> 200 actual
        engine.recordCorrection("oatmeal", shownKcal = 300.0, actualKcal = 200.0)

        // Second correction on a shown value of 265.5 kcal -> 200 actual
        engine.recordCorrection("oatmeal", shownKcal = 265.5, actualKcal = 200.0)
        val bias = engine.multiplierFor("oatmeal") // ~0.812

        // Bias moves progressively toward 0.6667 (200/300) without overshooting
        assertEquals(0.812, bias, 0.02)
    }

    @Test
    fun testCanonicalNameKey() {
        assertEquals("oatmeal", engine.keyFor("Oatmeal"))
        assertEquals("toast", engine.keyFor("Toast × 2 slices"))
        assertEquals("chickenbiryani", engine.keyFor("Chicken biryani (1 bowl, about 350 g)"))
    }
}
