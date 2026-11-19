package com.health.friday.device

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectArchiveAdaptersTest {

    @Test
    fun allAdapterTypeNamesAreUnique() {
        val types = listOf(
            StepsRecordAdapter.recordType,
            HeartRateRecordAdapter.recordType,
            SleepSessionRecordAdapter.recordType,
            WeightRecordAdapter.recordType,
            DistanceRecordAdapter.recordType,
            ActiveCaloriesRecordAdapter.recordType,
            TotalCaloriesRecordAdapter.recordType,
            FloorsClimbedRecordAdapter.recordType,
            BasalMetabolicRateRecordAdapter.recordType,
            BodyFatRecordAdapter.recordType,
            HeightRecordAdapter.recordType,
            Vo2MaxRecordAdapter.recordType,
            ExerciseSessionRecordAdapter.recordType,
            PowerRecordAdapter.recordType,
            SpeedRecordAdapter.recordType,
            StepsCadenceRecordAdapter.recordType
        )

        assertEquals(types.size, types.distinct().size)
    }

    @Test
    fun newAdapterTypeNamesAreStable() {
        assertEquals("distance", DistanceRecordAdapter.recordType)
        assertEquals("active_calories", ActiveCaloriesRecordAdapter.recordType)
        assertEquals("total_calories", TotalCaloriesRecordAdapter.recordType)
        assertEquals("floors_climbed", FloorsClimbedRecordAdapter.recordType)
        assertEquals("basal_metabolic_rate", BasalMetabolicRateRecordAdapter.recordType)
        assertEquals("body_fat", BodyFatRecordAdapter.recordType)
        assertEquals("height", HeightRecordAdapter.recordType)
        assertEquals("vo2_max", Vo2MaxRecordAdapter.recordType)
        assertEquals("exercise_session", ExerciseSessionRecordAdapter.recordType)
        assertEquals("power", PowerRecordAdapter.recordType)
        assertEquals("speed", SpeedRecordAdapter.recordType)
        assertEquals("steps_cadence", StepsCadenceRecordAdapter.recordType)
    }
}
