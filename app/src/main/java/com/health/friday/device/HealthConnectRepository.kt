package com.health.friday.device

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.kilocalories
import androidx.health.connect.client.units.grams
import androidx.health.connect.client.units.liters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class HealthSummary(
    val steps: Long,
    val heartRate: Long?,
    val sleepMinutes: Long
)

class HealthConnectRepository(
    context: Context
) {

    private val appContext = context.applicationContext

    private val providerPackageName =
        "com.google.android.apps.healthdata"

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(
            appContext,
            providerPackageName
        )
    }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(
            StepsRecord::class
        ),
        HealthPermission.getReadPermission(
            HeartRateRecord::class
        ),
        HealthPermission.getReadPermission(
            SleepSessionRecord::class
        ),
        HealthPermission.getWritePermission(
            NutritionRecord::class
        ),
        HealthPermission.getWritePermission(
            HydrationRecord::class
        )
    )

    fun isAvailable(): Boolean {

        val status =
            HealthConnectClient.getSdkStatus(
                appContext,
                providerPackageName
            )

        return status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAccess(): Boolean {

        if (!isAvailable()) {
            return false
        }

        return try {

            val granted =
                healthConnectClient
                    .permissionController
                    .getGrantedPermissions()

            granted.containsAll(requiredPermissions)

        } catch (e: Exception) {
            false
        }
    }

    /*
     * ---------------------------------------------------------
     * HEALTH
     * ---------------------------------------------------------
     */

    suspend fun getTodayHealth(): HealthSummary {

        if (!hasHealthReadAccess()) {
            return HealthSummary(
                steps = 0L,
                heartRate = null,
                sleepMinutes = 0L
            )
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()

        val startOfDay =
            today.atStartOfDay(zone).toInstant()

        val endOfDay =
            today.plusDays(1)
                .atStartOfDay(zone)
                .toInstant()

        val steps =
            getTodaySteps(
                startOfDay,
                endOfDay
            )

        val heartRate =
            getLatestHeartRate(
                startOfDay,
                now
            )

        val sleepMinutes =
            getTodaySleepMinutes(
                startOfDay,
                endOfDay
            )

        return HealthSummary(
            steps = steps,
            heartRate = heartRate,
            sleepMinutes = sleepMinutes
        )
    }

    private suspend fun hasHealthReadAccess(): Boolean {

        if (!isAvailable()) {
            return false
        }

        return try {

            val granted =
                healthConnectClient
                    .permissionController
                    .getGrantedPermissions()

            granted.contains(
                HealthPermission.getReadPermission(
                    StepsRecord::class
                )
            ) &&
                    granted.contains(
                        HealthPermission.getReadPermission(
                            HeartRateRecord::class
                        )
                    ) &&
                    granted.contains(
                        HealthPermission.getReadPermission(
                            SleepSessionRecord::class
                        )
                    )

        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getTodaySteps(
        start: Instant,
        end: Instant
    ): Long {

        val response =
            healthConnectClient.aggregate(
                AggregateRequest(
                    metrics =
                        setOf(
                            StepsRecord.COUNT_TOTAL
                        ),
                    timeRangeFilter =
                        TimeRangeFilter.between(
                            start,
                            end
                        )
                )
            )

        return response[
            StepsRecord.COUNT_TOTAL
        ] ?: 0L
    }

    private suspend fun getLatestHeartRate(
        start: Instant,
        end: Instant
    ): Long? {

        val response =
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType =
                        HeartRateRecord::class,
                    timeRangeFilter =
                        TimeRangeFilter.between(
                            start,
                            end
                        )
                )
            )

        val latestSample =
            response.records
                .flatMap { record ->
                    record.samples
                }
                .maxByOrNull { sample ->
                    sample.time
                }

        return latestSample?.beatsPerMinute
    }

    private suspend fun getTodaySleepMinutes(
        start: Instant,
        end: Instant
    ): Long {

        val response =
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType =
                        SleepSessionRecord::class,
                    timeRangeFilter =
                        TimeRangeFilter.between(
                            start,
                            end
                        )
                )
            )

        var totalMinutes = 0L

        for (session in response.records) {

            val sessionStart =
                if (session.startTime.isBefore(start)) {
                    start
                } else {
                    session.startTime
                }

            val sessionEnd =
                if (session.endTime.isAfter(end)) {
                    end
                } else {
                    session.endTime
                }

            if (sessionEnd.isAfter(sessionStart)) {

                totalMinutes +=
                    Duration.between(
                        sessionStart,
                        sessionEnd
                    ).toMinutes()
            }
        }

        return totalMinutes
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

        /*
         * NutritionRecord is an interval record.
         * We use a one-minute interval because FRIDAY
         * stores the meal as a point-in-time event.
         */
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