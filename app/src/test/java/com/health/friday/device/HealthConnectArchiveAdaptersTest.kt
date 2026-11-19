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
            StepsCadenceRecordAdapter.recordType,
            BloodGlucoseRecordAdapter.recordType,
            BloodPressureRecordAdapter.recordType,
            OxygenSaturationRecordAdapter.recordType,
            RespiratoryRateRecordAdapter.recordType,
            RestingHeartRateRecordAdapter.recordType,
            HeartRateVariabilityRecordAdapter.recordType,
            NutritionRecordAdapter.recordType,
            HydrationRecordAdapter.recordType,
            BodyTemperatureRecordAdapter.recordType,
            BodyWaterMassRecordAdapter.recordType,
            BoneMassRecordAdapter.recordType,
            CyclingPedalingCadenceRecordAdapter.recordType,
            ElevationGainedRecordAdapter.recordType,
            LeanBodyMassRecordAdapter.recordType,
            WheelchairPushesRecordAdapter.recordType
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
        assertEquals("blood_glucose", BloodGlucoseRecordAdapter.recordType)
        assertEquals("blood_pressure", BloodPressureRecordAdapter.recordType)
        assertEquals("oxygen_saturation", OxygenSaturationRecordAdapter.recordType)
        assertEquals("respiratory_rate", RespiratoryRateRecordAdapter.recordType)
        assertEquals("resting_heart_rate", RestingHeartRateRecordAdapter.recordType)
        assertEquals("heart_rate_variability_rmssd", HeartRateVariabilityRecordAdapter.recordType)
        assertEquals("nutrition", NutritionRecordAdapter.recordType)
        assertEquals("hydration", HydrationRecordAdapter.recordType)
        assertEquals("body_temperature", BodyTemperatureRecordAdapter.recordType)
        assertEquals("body_water_mass", BodyWaterMassRecordAdapter.recordType)
        assertEquals("bone_mass", BoneMassRecordAdapter.recordType)
        assertEquals("cycling_pedaling_cadence", CyclingPedalingCadenceRecordAdapter.recordType)
        assertEquals("elevation_gained", ElevationGainedRecordAdapter.recordType)
        assertEquals("lean_body_mass", LeanBodyMassRecordAdapter.recordType)
        assertEquals("wheelchair_pushes", WheelchairPushesRecordAdapter.recordType)
    }
}
