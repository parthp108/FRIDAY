package com.health.friday.device

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.grams
import androidx.health.connect.client.units.kilocalories
import androidx.health.connect.client.units.liters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class SleepStageSummary(
    val deepMinutes: Long = 0L,
    val remMinutes: Long = 0L,
    val lightMinutes: Long = 0L,
    val awakeMinutes: Long = 0L
)

data class WorkoutSummary(
    val type: String? = null,
    val title: String? = null,
    val durationMinutes: Long = 0L,
    val caloriesKcal: Int? = null,
    val distanceMeters: Double? = null,
    val avgHeartRate: Long? = null,
    val avgSpeedKmh: Double? = null,
    val avgCadenceSpm: Double? = null
)

data class BodySummary(
    val weightKg: Double? = null,
    val bodyFatPercentage: Double? = null,
    val heightMeters: Double? = null,
    val bmrKcal: Int? = null
)

data class VitalsSummary(
    val bloodGlucoseMmol: Double? = null,
    val systolicBp: Double? = null,
    val diastolicBp: Double? = null
)

data class HealthSummary(
    // Activity (7)
    val steps: Long = 0L,
    val distanceMeters: Double? = null,
    val isDistanceDerived: Boolean = false,
    val activeCaloriesKcal: Int? = null,
    val isCaloriesDerived: Boolean = false,
    val totalCaloriesKcal: Int? = null,
    val floorsClimbed: Double? = null,
    val activeTimeMinutes: Long = 0L,
    val powerWatts: Double? = null,

    // Heart & Vitals
    val heartRate: Long? = null,
    val restingHeartRate: Long? = null,
    val hrvRmssdMs: Double? = null,
    val exerciseHeartRate: Long? = null,
    val heartRateZone: String? = null,
    val vitals: VitalsSummary? = null,

    // Sleep
    val sleepMinutes: Long = 0L,
    val sleepStages: SleepStageSummary? = null,
    val napCount: Int = 0,
    val napTotalMinutes: Long = 0L,
    val sleepHeartRate: Long? = null,
    val sleepHrvRmssdMs: Double? = null,

    // Blood & Respiration
    val oxygenSaturationPercentage: Double? = null,
    val respiratoryRateBpm: Double? = null,

    // Workout
    val workout: WorkoutSummary? = null,

    // Wellness & Body
    val body: BodySummary? = null,
    val vo2Max: Double? = null,
    val stressScore: String? = null
)

// One calendar day of steps and sleep, used for the daily life snapshot.
data class DayHealth(
    val steps: Long?,
    val sleepMinutes: Long?,
    val sleepStartMillis: Long?,
    val sleepEndMillis: Long?
)

class HealthConnectRepository(
    context: Context
) {

    private val appContext = context.applicationContext

    private val providerPackageName =
        "com.google.android.apps.healthdata"

    val historyReadPermission: String =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    fun supportsHistoryRead(): Boolean {
        if (!isAvailable()) return false

        return try {
            healthConnectClient.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasHistoryReadAccess(): Boolean {
        return historyReadPermission in getGrantedPermissions()
    }

    fun obtainHealthConnectClient(): HealthConnectClient? {
        if (!isAvailable()) return null
        return try {
            healthConnectClient
        } catch (_: Exception) {
            null
        }
    }

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(
            appContext,
            providerPackageName
        )
    }

    val requiredPermissions: Set<String> = setOf(
        // Activity (7 requested by user + active calories/floors)
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(CyclingPedalingCadenceRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(WheelchairPushesRecord::class),

        // Body Measurements (4 requested by user + temperature / water / bone / lean)
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),

        // Nutrition & Hydration (1 requested by user for Nutrition + Hydration read/write)
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),

        // Sleep (1 requested by user)
        HealthPermission.getReadPermission(SleepSessionRecord::class),

        // Vitals (4 requested by user + resting HR / HRV / Respiratory)
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )

    fun isAvailable(): Boolean {
        val status =
            HealthConnectClient.getSdkStatus(
                appContext,
                providerPackageName
            )
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (!isAvailable()) return emptySet()
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun hasAccess(): Boolean {
        if (!isAvailable()) {
            return false
        }
        val granted = getGrantedPermissions()
        return granted.isNotEmpty()
    }

    suspend fun hasFullAccess(): Boolean {
        if (!isAvailable()) {
            return false
        }
        val granted = getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    fun openHealthConnectSettings(context: Context) {
        try {
            val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun isSamsungHealthInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSamsungHealth(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openHealthConnectSettings(context)
            }
        } catch (_: Exception) {
            openHealthConnectSettings(context)
        }
    }

    /*
     * ---------------------------------------------------------
     * HEALTH
     * ---------------------------------------------------------
     */

    suspend fun getTodayHealth(): HealthSummary {
        if (!isAvailable()) {
            return HealthSummary()
        }

        val granted = try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            emptySet()
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant()

        // 1. STEPS (Deduplicated with Samsung Health / Band as primary source)
        val steps = getTodaySteps(startOfDay, endOfDay) ?: 0L

        // 2. DISTANCE
        val distanceMeters = try {
            if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                if (res.records.isNotEmpty()) {
                    val byOrigin = res.records.groupBy { it.metadata.dataOrigin.packageName }
                    val shealth = byOrigin.entries.firstOrNull { (pkg, _) ->
                        pkg.contains("shealth", ignoreCase = true) || pkg.contains("samsung", ignoreCase = true)
                    }?.value
                    if (!shealth.isNullOrEmpty()) {
                        shealth.sumOf { it.distance.inMeters }
                    } else {
                        byOrigin.map { (_, recs) -> recs.sumOf { it.distance.inMeters } }.maxOrNull()
                    }
                } else null
            } else null
        } catch (_: Exception) { null }

        // 3. ACTIVE CALORIES
        val activeCaloriesKcal = try {
            if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = ActiveCaloriesBurnedRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                if (res.records.isNotEmpty()) {
                    val byOrigin = res.records.groupBy { it.metadata.dataOrigin.packageName }
                    val shealth = byOrigin.entries.firstOrNull { (pkg, _) ->
                        pkg.contains("shealth", ignoreCase = true) || pkg.contains("samsung", ignoreCase = true)
                    }?.value
                    if (!shealth.isNullOrEmpty()) {
                        shealth.sumOf { it.energy.inKilocalories }.toInt()
                    } else {
                        byOrigin.map { (_, recs) -> recs.sumOf { it.energy.inKilocalories } }.maxOrNull()?.toInt()
                    }
                } else null
            } else null
        } catch (_: Exception) { null }

        // 4. TOTAL CALORIES
        val totalCaloriesKcal = try {
            if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                val res = healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt()
            } else null
        } catch (_: Exception) { null }

        // 5. FLOORS CLIMBED
        val floorsClimbed = try {
            if (granted.contains(HealthPermission.getReadPermission(FloorsClimbedRecord::class))) {
                val res = healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                res[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
            } else null
        } catch (_: Exception) { null }

        // 6. HEART RATE (Latest)
        val heartRate = getLatestHeartRate(startOfDay, now)

        // 7. RESTING HR
        val restingHeartRate = try {
            if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(7)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.beatsPerMinute
            } else null
        } catch (_: Exception) { null }

        // 8. HRV
        val hrvRmssdMs = try {
            if (granted.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(7)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.heartRateVariabilityMillis
            } else null
        } catch (_: Exception) { null }

        // 9. SLEEP & STAGES & NAPS
        val sleepRecords = try {
            if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofHours(18)), endOfDay)
                    )
                ).records.filter { !it.endTime.isBefore(startOfDay) && it.endTime.isBefore(endOfDay) }
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        var totalSleepMinutes = 0L
        var deepMins = 0L
        var remMins = 0L
        var lightMins = 0L
        var awakeMins = 0L
        var napCount = 0
        var napTotalMins = 0L
        var mainSleepSession: SleepSessionRecord? = null

        for (session in sleepRecords) {
            val dur = Duration.between(session.startTime, session.endTime).toMinutes()
            val startHour = session.startTime.atZone(zone).hour
            if (dur < 180L || startHour in 8..19) {
                napCount++
                napTotalMins += dur
            } else {
                totalSleepMinutes += dur
                if (mainSleepSession == null || dur > Duration.between(mainSleepSession.startTime, mainSleepSession.endTime).toMinutes()) {
                    mainSleepSession = session
                }
            }

            for (stage in session.stages) {
                val stageDur = Duration.between(stage.startTime, stage.endTime).toMinutes()
                when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> deepMins += stageDur
                    SleepSessionRecord.STAGE_TYPE_REM -> remMins += stageDur
                    SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> lightMins += stageDur
                    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awakeMins += stageDur
                }
            }
        }
        if (totalSleepMinutes == 0L && sleepRecords.isNotEmpty()) {
            totalSleepMinutes = sleepRecords.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }

        val sleepStages = if (deepMins > 0 || remMins > 0 || lightMins > 0 || awakeMins > 0) {
            SleepStageSummary(deepMinutes = deepMins, remMinutes = remMins, lightMinutes = lightMins, awakeMinutes = awakeMins)
        } else null

        // SLEEP HR & SLEEP HRV
        var sleepHeartRate: Long? = null
        var sleepHrvRmssdMs: Double? = null
        if (mainSleepSession != null) {
            val sleepStart = mainSleepSession.startTime
            val sleepEnd = mainSleepSession.endTime
            try {
                if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                    val hrRes = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sleepStart, sleepEnd)
                        )
                    )
                    val samples = hrRes.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        sleepHeartRate = (samples.sumOf { it.beatsPerMinute }.toDouble() / samples.size).toLong()
                    }
                }
            } catch (_: Exception) {}

            try {
                if (granted.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))) {
                    val hrvRes = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateVariabilityRmssdRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sleepStart, sleepEnd)
                        )
                    )
                    if (hrvRes.records.isNotEmpty()) {
                        sleepHrvRmssdMs = hrvRes.records.map { it.heartRateVariabilityMillis }.average()
                    }
                }
            } catch (_: Exception) {}
        }

        // 10. BLOOD & RESPIRATION
        val oxygenSat = try {
            if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = OxygenSaturationRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(1)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.percentage?.value
            } else null
        } catch (_: Exception) { null }

        val respRate = try {
            if (granted.contains(HealthPermission.getReadPermission(RespiratoryRateRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = RespiratoryRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(1)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.rate
            } else null
        } catch (_: Exception) { null }

        // 11. WORKOUT & EXERCISE SESSIONS
        val exerciseSessions = try {
            if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                ).records
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        val activeTimeMinutes = exerciseSessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

        val latestWorkoutSession = exerciseSessions.maxByOrNull { it.endTime }
        var workoutSummary: WorkoutSummary? = null
        var exerciseHeartRate: Long? = null

        if (latestWorkoutSession != null) {
            val wStart = latestWorkoutSession.startTime
            val wEnd = latestWorkoutSession.endTime
            val durMins = Duration.between(wStart, wEnd).toMinutes()
            val wTitle = latestWorkoutSession.title
            val wTypeName = formatExerciseType(latestWorkoutSession.exerciseType)

            val wCalories = try {
                if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
                    val res = healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(wStart, wEnd)
                        )
                    )
                    res[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt()
                } else null
            } catch (_: Exception) { null }

            val wDistance = try {
                if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                    val res = healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(wStart, wEnd)
                        )
                    )
                    res[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                } else null
            } catch (_: Exception) { null }

            val wAvgHr = try {
                if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                    val hrRes = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(wStart, wEnd)
                        )
                    )
                    val samples = hrRes.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        (samples.sumOf { it.beatsPerMinute }.toDouble() / samples.size).toLong()
                    } else null
                } else null
            } catch (_: Exception) { null }

            exerciseHeartRate = wAvgHr

            var avgSpeedKmh: Double? = null
            if (wDistance != null && durMins > 0) {
                avgSpeedKmh = (wDistance / 1000.0) / (durMins / 60.0)
            } else try {
                if (granted.contains(HealthPermission.getReadPermission(SpeedRecord::class))) {
                    val speedRes = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = SpeedRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(wStart, wEnd)
                        )
                    )
                    val samples = speedRes.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        samples.map { it.speed.inMetersPerSecond * 3.6 }.average()
                    } else null
                } else null
            } catch (_: Exception) { null }

            val avgCadence = try {
                if (granted.contains(HealthPermission.getReadPermission(StepsCadenceRecord::class))) {
                    val cadRes = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = StepsCadenceRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(wStart, wEnd)
                        )
                    )
                    val samples = cadRes.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        samples.map { it.rate }.average()
                    } else null
                } else null
            } catch (_: Exception) { null }

            workoutSummary = WorkoutSummary(
                type = wTypeName,
                title = wTitle,
                durationMinutes = durMins,
                caloriesKcal = wCalories,
                distanceMeters = wDistance,
                avgHeartRate = wAvgHr,
                avgSpeedKmh = avgSpeedKmh,
                avgCadenceSpm = avgCadence
            )
        }

        // HR ZONE Calculation
        val hrZoneStr = heartRate?.let { hr ->
            when {
                hr < 100 -> "Warmup ($hr bpm)"
                hr in 100..120 -> "Zone 1 ($hr bpm)"
                hr in 121..140 -> "Zone 2 - Fat Burn ($hr bpm)"
                hr in 141..160 -> "Zone 3 - Aerobic ($hr bpm)"
                hr in 161..180 -> "Zone 4 - Anaerobic ($hr bpm)"
                else -> "Zone 5 - Peak ($hr bpm)"
            }
        }

        // 12. VO2 MAX
        val vo2Max = try {
            if (granted.contains(HealthPermission.getReadPermission(Vo2MaxRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = Vo2MaxRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(30)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.vo2MillilitersPerMinuteKilogram
            } else null
        } catch (_: Exception) { null }

        // 13. BODY DATA (Weight, Body Fat, Height, BMR)
        val weightKg = try {
            if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(30)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.weight?.inKilograms
            } else null
        } catch (_: Exception) { null }

        val bodyFatPct = try {
            if (granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = BodyFatRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(30)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.percentage?.value
            } else null
        } catch (_: Exception) { null }

        val heightMeters = try {
            if (granted.contains(HealthPermission.getReadPermission(HeightRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = HeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(365)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.height?.inMeters
            } else null
        } catch (_: Exception) { null }

        val bmrKcal = try {
            if (granted.contains(HealthPermission.getReadPermission(BasalMetabolicRateRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = BasalMetabolicRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(30)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.basalMetabolicRate?.inKilocaloriesPerDay?.toInt()
            } else null
        } catch (_: Exception) { null }

        val bodySummary = if (weightKg != null || bodyFatPct != null || heightMeters != null || bmrKcal != null) {
            BodySummary(weightKg = weightKg, bodyFatPercentage = bodyFatPct, heightMeters = heightMeters, bmrKcal = bmrKcal)
        } else null

        // 14. VITALS (Blood Glucose, Blood Pressure)
        val bloodGlucose = try {
            if (granted.contains(HealthPermission.getReadPermission(BloodGlucoseRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = BloodGlucoseRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(7)), now)
                    )
                )
                res.records.maxByOrNull { it.time }?.level?.inMillimolesPerLiter
            } else null
        } catch (_: Exception) { null }

        val bloodPressureRecord = try {
            if (granted.contains(HealthPermission.getReadPermission(BloodPressureRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = BloodPressureRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay.minus(Duration.ofDays(7)), now)
                    )
                )
                res.records.maxByOrNull { it.time }
            } else null
        } catch (_: Exception) { null }

        val vitalsSummary = if (bloodGlucose != null || bloodPressureRecord != null) {
            VitalsSummary(
                bloodGlucoseMmol = bloodGlucose,
                systolicBp = bloodPressureRecord?.systolic?.inMillimetersOfMercury,
                diastolicBp = bloodPressureRecord?.diastolic?.inMillimetersOfMercury
            )
        } else null

        // 15. POWER
        val powerWatts = try {
            if (granted.contains(HealthPermission.getReadPermission(PowerRecord::class))) {
                val res = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = PowerRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                    )
                )
                val samples = res.records.flatMap { it.samples }
                if (samples.isNotEmpty()) {
                    samples.map { it.power.inWatts }.average()
                } else null
            } else null
        } catch (_: Exception) { null }

        // Real or step-derived calculations (no fake dummy example numbers)
        val finalDistanceMeters = distanceMeters ?: if (steps > 0) steps * 0.762 else null
        val isDistanceDerived = distanceMeters == null && steps > 0

        val finalActiveCalories = activeCaloriesKcal ?: if (steps > 0) (steps * 0.04).toInt() else null
        val isCaloriesDerived = activeCaloriesKcal == null && steps > 0

        val finalTotalCalories = totalCaloriesKcal ?: finalActiveCalories?.let { it + (bmrKcal ?: 1650) }

        val finalRestingHr = restingHeartRate ?: sleepHeartRate
        val finalHrv = hrvRmssdMs ?: sleepHrvRmssdMs

        val calcStress = if (heartRate != null && finalHrv != null) {
            val score = ((heartRate * 0.7) - finalHrv * 0.3 + 15).toInt().coerceIn(12, 85)
            when {
                score < 30 -> "Relaxed ($score/100)"
                score < 50 -> "Low ($score/100)"
                score < 70 -> "Moderate ($score/100)"
                else -> "High ($score/100)"
            }
        } else null

        return HealthSummary(
            steps = steps,
            distanceMeters = finalDistanceMeters,
            isDistanceDerived = isDistanceDerived,
            activeCaloriesKcal = finalActiveCalories,
            isCaloriesDerived = isCaloriesDerived,
            totalCaloriesKcal = finalTotalCalories,
            floorsClimbed = floorsClimbed,
            activeTimeMinutes = activeTimeMinutes,
            powerWatts = powerWatts,
            heartRate = heartRate,
            restingHeartRate = finalRestingHr,
            hrvRmssdMs = finalHrv,
            exerciseHeartRate = exerciseHeartRate,
            heartRateZone = hrZoneStr,
            vitals = vitalsSummary,
            sleepMinutes = totalSleepMinutes,
            sleepStages = sleepStages,
            napCount = napCount,
            napTotalMinutes = napTotalMins,
            sleepHeartRate = sleepHeartRate,
            sleepHrvRmssdMs = sleepHrvRmssdMs,
            oxygenSaturationPercentage = oxygenSat,
            respiratoryRateBpm = respRate,
            workout = workoutSummary,
            body = bodySummary,
            vo2Max = vo2Max,
            stressScore = calcStress
        )
    }

    private fun formatExerciseType(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Stair Climbing"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            else -> "Workout"
        }
    }

    private suspend fun getLatestHeartRate(
        start: Instant,
        end: Instant
    ): Long? {
        val response =
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
        val latestSample =
            response.records
                .flatMap { record -> record.samples }
                .maxByOrNull { sample -> sample.time }
        return latestSample?.beatsPerMinute
    }

    private suspend fun getTodaySteps(
        start: Instant,
        end: Instant
    ): Long? {
        return try {
            val res = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            res[StepsRecord.COUNT_TOTAL]
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getDayHealth(dayStartMillis: Long): DayHealth? {
        if (!isAvailable()) return null

        val granted = getGrantedPermissions()

        val canReadSteps =
            HealthPermission.getReadPermission(StepsRecord::class) in granted

        val canReadSleep =
            HealthPermission.getReadPermission(SleepSessionRecord::class) in granted

        if (!canReadSteps && !canReadSleep) return null

        val zone = ZoneId.systemDefault()
        val startOfDay = Instant.ofEpochMilli(dayStartMillis)

        val endOfDay = startOfDay.atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()

        val steps = if (canReadSteps) {
            getTodaySteps(startOfDay, endOfDay)
        } else {
            null
        }

        val sessions = if (canReadSleep) {
            try {
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.minus(Duration.ofHours(18)),
                            endOfDay
                        )
                    )
                ).records.filter { session ->
                    !session.endTime.isBefore(startOfDay) &&
                        session.endTime.isBefore(endOfDay)
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val sleepMinutes = sessions?.sumOf {
            Duration.between(it.startTime, it.endTime).toMinutes()
        }

        return DayHealth(
            steps = steps,
            sleepMinutes = sleepMinutes,
            sleepStartMillis = sessions?.minOfOrNull {
                it.startTime.toEpochMilli()
            },
            sleepEndMillis = sessions?.maxOfOrNull {
                it.endTime.toEpochMilli()
            }
        )
    }

    /*
     * ---------------------------------------------------------
     * NUTRITION
     * ---------------------------------------------------------
     */

    suspend fun writeMeal(
        mealId: Long,
        name: String,
        mealType: String,
        calories: Int,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        timestamp: Long
    ) {
        if (!hasWritePermission(
                HealthPermission.getWritePermission(
                    NutritionRecord::class
                )
            )
        ) {
            return
        }
        val startTime =
            Instant.ofEpochMilli(timestamp)
        val endTime =
            startTime.plusSeconds(60)
        val zoneOffset =
            ZoneId.systemDefault()
                .rules
                .getOffset(startTime)

        val record =
            NutritionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                metadata =
                    Metadata.manualEntry(
                        clientRecordId =
                            "friday-meal-$mealId"
                    ),
                energy =
                    calories.toDouble().kilocalories,
                protein =
                    protein.grams,
                totalCarbohydrate =
                    carbohydrates.grams,
                totalFat =
                    fat.grams,
                name = name,
                mealType =
                    healthConnectMealType(
                        mealType
                    )
            )

        healthConnectClient.insertRecords(
            listOf(record)
        )
    }

    suspend fun deleteMeal(
        mealId: Long
    ) {
        if (!hasWritePermission(
                HealthPermission.getWritePermission(
                    NutritionRecord::class
                )
            )
        ) {
            return
        }
        healthConnectClient.deleteRecords(
            recordType = NutritionRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = listOf("friday-meal-$mealId")
        )
    }

    /*
     * ---------------------------------------------------------
     * HYDRATION
     * ---------------------------------------------------------
     */

    suspend fun writeWater(
        waterId: Long,
        amountMl: Int,
        timestamp: Long
    ) {
        if (!hasWritePermission(
                HealthPermission.getWritePermission(
                    HydrationRecord::class
                )
            )
        ) {
            return
        }
        val startTime =
            Instant.ofEpochMilli(timestamp)
        val endTime =
            startTime.plusSeconds(60)
        val zoneOffset =
            ZoneId.systemDefault()
                .rules
                .getOffset(startTime)

        val record =
            HydrationRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                volume =
                    (amountMl / 1000.0).liters,
                metadata =
                    Metadata.manualEntry(
                        clientRecordId =
                            "friday-water-$waterId"
                    )
            )

        healthConnectClient.insertRecords(
            listOf(record)
        )
    }

    suspend fun deleteWater(
        waterId: Long
    ) {
        if (!hasWritePermission(
                HealthPermission.getWritePermission(
                    HydrationRecord::class
                )
            )
        ) {
            return
        }
        healthConnectClient.deleteRecords(
            recordType = HydrationRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = listOf("friday-water-$waterId")
        )
    }

    /*
     * ---------------------------------------------------------
     * HELPERS
     * ---------------------------------------------------------
     */

    private suspend fun hasWritePermission(
        permission: String
    ): Boolean {
        return try {
            healthConnectClient
                .permissionController
                .getGrantedPermissions()
                .contains(permission)
        } catch (e: Exception) {
            false
        }
    }

    private fun healthConnectMealType(
        mealType: String
    ): Int {
        return when (
            mealType.lowercase()
        ) {
            "breakfast" ->
                MealType.MEAL_TYPE_BREAKFAST
            "lunch" ->
                MealType.MEAL_TYPE_LUNCH
            "dinner" ->
                MealType.MEAL_TYPE_DINNER
            "snack" ->
                MealType.MEAL_TYPE_SNACK
            else ->
                MealType.MEAL_TYPE_UNKNOWN
        }
    }
}
