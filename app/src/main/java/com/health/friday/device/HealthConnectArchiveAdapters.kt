package com.health.friday.device

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import com.health.friday.data.local.HealthRecordArchiveEntity
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.HealthRecordSyncPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

interface HealthRecordAdapter<T : Record> {
    val recordType: String
    fun serializePayload(record: T): JsonObject
    fun extractStartTimeMs(record: T): Long?
    fun extractEndTimeMs(record: T): Long?
}

object StepsRecordAdapter : HealthRecordAdapter<StepsRecord> {
    override val recordType: String = "steps"

    override fun serializePayload(record: StepsRecord): JsonObject = buildJsonObject {
        put("count", record.count)
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: StepsRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: StepsRecord): Long = record.endTime.toEpochMilli()
}

object HeartRateRecordAdapter : HealthRecordAdapter<HeartRateRecord> {
    override val recordType: String = "heart_rate"

    override fun serializePayload(record: HeartRateRecord): JsonObject = buildJsonObject {
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
        put("samples", buildJsonArray {
            record.samples.forEach { sample ->
                add(buildJsonObject {
                    put("beatsPerMinute", sample.beatsPerMinute)
                    put("time", sample.time.toEpochMilli())
                })
            }
        })
    }

    override fun extractStartTimeMs(record: HeartRateRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: HeartRateRecord): Long = record.endTime.toEpochMilli()
}

object SleepSessionRecordAdapter : HealthRecordAdapter<SleepSessionRecord> {
    override val recordType: String = "sleep_session"

    override fun serializePayload(record: SleepSessionRecord): JsonObject = buildJsonObject {
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
        record.title?.let { put("title", it) }
        record.notes?.let { put("notes", it) }
        put("stages", buildJsonArray {
            record.stages.forEach { stage ->
                add(buildJsonObject {
                    put("stage", stage.stage)
                    put("startTime", stage.startTime.toEpochMilli())
                    put("endTime", stage.endTime.toEpochMilli())
                })
            }
        })
    }

    override fun extractStartTimeMs(record: SleepSessionRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: SleepSessionRecord): Long = record.endTime.toEpochMilli()
}

object WeightRecordAdapter : HealthRecordAdapter<WeightRecord> {
    override val recordType: String = "weight"

    override fun serializePayload(record: WeightRecord): JsonObject = buildJsonObject {
        put("time", record.time.toEpochMilli())
        put("weightKg", record.weight.inKilograms)
        record.zoneOffset?.let { put("zoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: WeightRecord): Long = record.time.toEpochMilli()
    override fun extractEndTimeMs(record: WeightRecord): Long = record.time.toEpochMilli()
}

object DistanceRecordAdapter : HealthRecordAdapter<DistanceRecord> {
    override val recordType: String = "distance"

    override fun serializePayload(record: DistanceRecord): JsonObject = buildJsonObject {
        put("distanceMeters", record.distance.inMeters)
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: DistanceRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: DistanceRecord): Long = record.endTime.toEpochMilli()
}

object ActiveCaloriesRecordAdapter : HealthRecordAdapter<ActiveCaloriesBurnedRecord> {
    override val recordType: String = "active_calories"

    override fun serializePayload(record: ActiveCaloriesBurnedRecord): JsonObject = buildJsonObject {
        put("energyKcal", record.energy.inKilocalories)
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: ActiveCaloriesBurnedRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: ActiveCaloriesBurnedRecord): Long = record.endTime.toEpochMilli()
}

object TotalCaloriesRecordAdapter : HealthRecordAdapter<TotalCaloriesBurnedRecord> {
    override val recordType: String = "total_calories"

    override fun serializePayload(record: TotalCaloriesBurnedRecord): JsonObject = buildJsonObject {
        put("energyKcal", record.energy.inKilocalories)
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: TotalCaloriesBurnedRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: TotalCaloriesBurnedRecord): Long = record.endTime.toEpochMilli()
}

object FloorsClimbedRecordAdapter : HealthRecordAdapter<FloorsClimbedRecord> {
    override val recordType: String = "floors_climbed"

    override fun serializePayload(record: FloorsClimbedRecord): JsonObject = buildJsonObject {
        put("floors", record.floors)
        put("startTime", record.startTime.toEpochMilli())
        put("endTime", record.endTime.toEpochMilli())
        record.startZoneOffset?.let { put("startZoneOffset", it.toString()) }
        record.endZoneOffset?.let { put("endZoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: FloorsClimbedRecord): Long = record.startTime.toEpochMilli()
    override fun extractEndTimeMs(record: FloorsClimbedRecord): Long = record.endTime.toEpochMilli()
}

object BasalMetabolicRateRecordAdapter : HealthRecordAdapter<BasalMetabolicRateRecord> {
    override val recordType: String = "basal_metabolic_rate"

    override fun serializePayload(record: BasalMetabolicRateRecord): JsonObject = buildJsonObject {
        put("kilocaloriesPerDay", record.basalMetabolicRate.inKilocaloriesPerDay)
        put("time", record.time.toEpochMilli())
        record.zoneOffset?.let { put("zoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: BasalMetabolicRateRecord): Long = record.time.toEpochMilli()
    override fun extractEndTimeMs(record: BasalMetabolicRateRecord): Long = record.time.toEpochMilli()
}

object BodyFatRecordAdapter : HealthRecordAdapter<BodyFatRecord> {
    override val recordType: String = "body_fat"

    override fun serializePayload(record: BodyFatRecord): JsonObject = buildJsonObject {
        put("percentage", record.percentage.value)
        put("time", record.time.toEpochMilli())
        record.zoneOffset?.let { put("zoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: BodyFatRecord): Long = record.time.toEpochMilli()
    override fun extractEndTimeMs(record: BodyFatRecord): Long = record.time.toEpochMilli()
}

object HeightRecordAdapter : HealthRecordAdapter<HeightRecord> {
    override val recordType: String = "height"

    override fun serializePayload(record: HeightRecord): JsonObject = buildJsonObject {
        put("heightMeters", record.height.inMeters)
        put("time", record.time.toEpochMilli())
        record.zoneOffset?.let { put("zoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: HeightRecord): Long = record.time.toEpochMilli()
    override fun extractEndTimeMs(record: HeightRecord): Long = record.time.toEpochMilli()
}

object Vo2MaxRecordAdapter : HealthRecordAdapter<Vo2MaxRecord> {
    override val recordType: String = "vo2_max"

    override fun serializePayload(record: Vo2MaxRecord): JsonObject = buildJsonObject {
        put("vo2MillilitersPerMinuteKilogram", record.vo2MillilitersPerMinuteKilogram)
        put("measurementMethod", record.measurementMethod)
        put("time", record.time.toEpochMilli())
        record.zoneOffset?.let { put("zoneOffset", it.toString()) }
    }

    override fun extractStartTimeMs(record: Vo2MaxRecord): Long = record.time.toEpochMilli()
    override fun extractEndTimeMs(record: Vo2MaxRecord): Long = record.time.toEpochMilli()
}

fun <T : Record> T.toArchiveEntity(
    sourceDeviceId: String,
    adapter: HealthRecordAdapter<T>,
    syncHlc: String? = null
): HealthRecordArchiveEntity {
    val meta = metadata
    val recordId = meta.id
    val archiveId = "$sourceDeviceId:${adapter.recordType}:$recordId"
    val payloadObj = adapter.serializePayload(this)

    return HealthRecordArchiveEntity(
        archiveId = archiveId,
        sourceDeviceId = sourceDeviceId,
        recordType = adapter.recordType,
        recordId = recordId,
        dataOriginPackage = meta.dataOrigin.packageName,
        clientRecordId = meta.clientRecordId,
        clientRecordVersion = meta.clientRecordVersion,
        lastModifiedTimeMs = meta.lastModifiedTime.toEpochMilli(),
        recordingMethod = meta.recordingMethod,
        deviceManufacturer = meta.device?.manufacturer,
        deviceModel = meta.device?.model,
        deviceType = meta.device?.type,
        startTimeMs = adapter.extractStartTimeMs(this),
        endTimeMs = adapter.extractEndTimeMs(this),
        payloadJson = payloadObj.toString(),
        syncHlc = syncHlc,
        isDeleted = false
    )
}

fun HealthRecordArchiveEntity.toCloudSyncPayload(): JsonObject =
    Json.encodeToJsonElement(
        HealthRecordSyncPayload.serializer(),
        HealthRecordSyncPayload(
            archiveId = archiveId,
            sourceDeviceId = sourceDeviceId,
            recordType = recordType,
            recordId = recordId,
            dataOriginPackage = dataOriginPackage,
            clientRecordId = clientRecordId,
            clientRecordVersion = clientRecordVersion,
            lastModifiedTimeMs = lastModifiedTimeMs,
            recordingMethod = recordingMethod,
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            deviceType = deviceType,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            payloadJson = payloadJson
        )
    ).jsonObject
