package com.health.friday.device

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.health.friday.data.local.HealthConnectArchiveDao
import com.health.friday.data.local.HealthConnectSyncStateEntity
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.SyncOperationType
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncBatchMutation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

private data class RecordSyncTarget<T : Record>(
    val kclass: KClass<T>,
    val adapter: HealthRecordAdapter<T>
)

data class HealthConnectArchiveSyncReport(
    val importedByRecordType: Map<String, Int>,
    val failuresByRecordType: Map<String, String>
)

internal fun HealthConnectSyncStateEntity.resetAfterChangesTokenExpiry(): HealthConnectSyncStateEntity =
    copy(
        changesToken = null,
        baselinePageToken = null,
        baselineFromEpochMs = null,
        baselineToEpochMs = null,
        baselineStartedAtMs = null,
        baselineCompletedAtMs = null,
        baselineComplete = false,
        lastSyncAtMs = System.currentTimeMillis()
    )

class HealthConnectArchiveSyncer(
    private val healthConnectRepo: HealthConnectRepository,
    private val archiveDao: HealthConnectArchiveDao,
    private val cloudSyncWriter: AndroidCloudSyncWriter,
    private val sourceDeviceId: String
) {
    private val syncMutex = Mutex()

    suspend fun syncAllRecordTypes(): HealthConnectArchiveSyncReport =
        syncMutex.withLock {
            val results = mutableMapOf<String, Int>()
            val failures = mutableMapOf<String, String>()

            if (!healthConnectRepo.isAvailable()) {
                return@withLock HealthConnectArchiveSyncReport(
                    importedByRecordType = results,
                    failuresByRecordType = mapOf(
                        "health_connect" to "Health Connect is unavailable"
                    )
                )
            }

            val targets: List<RecordSyncTarget<out Record>> = listOf(
                RecordSyncTarget(StepsRecord::class, StepsRecordAdapter),
                RecordSyncTarget(HeartRateRecord::class, HeartRateRecordAdapter),
                RecordSyncTarget(SleepSessionRecord::class, SleepSessionRecordAdapter),
                RecordSyncTarget(WeightRecord::class, WeightRecordAdapter),
                RecordSyncTarget(DistanceRecord::class, DistanceRecordAdapter),
                RecordSyncTarget(ActiveCaloriesBurnedRecord::class, ActiveCaloriesRecordAdapter),
                RecordSyncTarget(TotalCaloriesBurnedRecord::class, TotalCaloriesRecordAdapter),
                RecordSyncTarget(FloorsClimbedRecord::class, FloorsClimbedRecordAdapter),
                RecordSyncTarget(BasalMetabolicRateRecord::class, BasalMetabolicRateRecordAdapter),
                RecordSyncTarget(BodyFatRecord::class, BodyFatRecordAdapter),
                RecordSyncTarget(HeightRecord::class, HeightRecordAdapter),
                RecordSyncTarget(Vo2MaxRecord::class, Vo2MaxRecordAdapter),
                RecordSyncTarget(ExerciseSessionRecord::class, ExerciseSessionRecordAdapter),
                RecordSyncTarget(PowerRecord::class, PowerRecordAdapter),
                RecordSyncTarget(SpeedRecord::class, SpeedRecordAdapter),
                RecordSyncTarget(StepsCadenceRecord::class, StepsCadenceRecordAdapter),
                RecordSyncTarget(BloodGlucoseRecord::class, BloodGlucoseRecordAdapter),
                RecordSyncTarget(BloodPressureRecord::class, BloodPressureRecordAdapter),
                RecordSyncTarget(OxygenSaturationRecord::class, OxygenSaturationRecordAdapter),
                RecordSyncTarget(RespiratoryRateRecord::class, RespiratoryRateRecordAdapter),
                RecordSyncTarget(RestingHeartRateRecord::class, RestingHeartRateRecordAdapter),
                RecordSyncTarget(HeartRateVariabilityRmssdRecord::class, HeartRateVariabilityRecordAdapter),
                RecordSyncTarget(NutritionRecord::class, NutritionRecordAdapter),
                RecordSyncTarget(HydrationRecord::class, HydrationRecordAdapter)
            )

            val granted = healthConnectRepo.getGrantedPermissions()

            for (target in targets) {
                val typeName = target.adapter.recordType
                val permission = HealthPermission.getReadPermission(target.kclass)

                if (permission !in granted) continue

                try {
                    results[typeName] = syncTarget(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures[typeName] = e.message ?: e.javaClass.simpleName
                }
            }

            HealthConnectArchiveSyncReport(
                importedByRecordType = results,
                failuresByRecordType = failures
            )
        }

    private suspend fun <T : Record> syncTarget(
        target: RecordSyncTarget<T>
    ): Int {
        val kclass = target.kclass
        val adapter = target.adapter

        val client = healthConnectRepo.obtainHealthConnectClient() ?: return 0
        val typeName = adapter.recordType

        var state = archiveDao.getSyncState(typeName) ?: HealthConnectSyncStateEntity(recordType = typeName)

        // 1. Ensure Changes Token exists BEFORE starting baseline import
        if (state.changesToken.isNullOrBlank()) {
            val token = client.getChangesToken(
                ChangesTokenRequest(recordTypes = setOf(kclass))
            )

            if (token.isBlank()) {
                throw IllegalStateException(
                    "Health Connect returned an empty changes token for $typeName"
                )
            }

            state = state.copy(changesToken = token)
            archiveDao.saveSyncState(state)
        }

        var importedCount = 0

        // 2. Perform or resume Baseline Historical Import if not complete
        if (!state.baselineComplete) {
            importedCount += runBaselineImport(client, kclass, adapter, state)
            state = archiveDao.getSyncState(typeName) ?: state
        }

        // 3. Process Differential Changes API updates & deletions
        val token = state.changesToken
        if (!token.isNullOrBlank() && state.baselineComplete) {
            importedCount += processChanges(client, adapter, state, token)
        }

        return importedCount
    }

    private suspend fun <T : Record> runBaselineImport(
        client: HealthConnectClient,
        kclass: KClass<T>,
        adapter: HealthRecordAdapter<T>,
        initialState: HealthConnectSyncStateEntity
    ): Int {
        val nowMs = System.currentTimeMillis()

        val baselineStartedAtMs = initialState.baselineStartedAtMs ?: nowMs
        val baselineToMs = initialState.baselineToEpochMs ?: nowMs

        val hasHistoryPermission = healthConnectRepo.supportsHistoryRead() &&
                healthConnectRepo.hasHistoryReadAccess()

        val baselineFromMs = initialState.baselineFromEpochMs
            ?: if (hasHistoryPermission) {
                Instant.EPOCH.toEpochMilli()
            } else {
                baselineToMs - Duration.ofDays(30).toMillis()
            }

        val startTime = Instant.ofEpochMilli(baselineFromMs)
        val endTime = Instant.ofEpochMilli(baselineToMs)

        // Save the fixed bounds before the first page request
        var state = initialState.copy(
            baselineFromEpochMs = baselineFromMs,
            baselineToEpochMs = baselineToMs,
            baselineStartedAtMs = baselineStartedAtMs,
            baselineComplete = false
        )

        archiveDao.saveSyncState(state)

        var pageToken = state.baselinePageToken
        var totalImported = 0

        while (true) {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = kclass,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = pageToken,
                    pageSize = 200
                )
            )

            val records = response.records.filter {
                it.metadata.dataOrigin.packageName != "com.health.friday"
            }

            val mutations = records.map { record ->
                val archiveEntity = record.toArchiveEntity(sourceDeviceId, adapter)

                CloudSyncBatchMutation(
                    entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                    entityId = archiveEntity.archiveId,
                    operationType = SyncOperationType.UPSERT
                ) { hlc ->
                    val stamped = archiveEntity.copy(syncHlc = hlc)
                    archiveDao.upsertRecord(stamped)
                    stamped.toCloudSyncPayload()
                }
            }

            val nextPageToken = response.pageToken
            val isComplete = nextPageToken.isNullOrEmpty()
            val completedAtMs = if (isComplete) System.currentTimeMillis() else null

            val updatedState = state.copy(
                baselinePageToken = if (isComplete) null else nextPageToken,
                baselineComplete = isComplete,
                baselineCompletedAtMs = completedAtMs,
                lastSyncAtMs = System.currentTimeMillis()
            )

            cloudSyncWriter.upsertBatch(mutations) {
                archiveDao.saveSyncState(updatedState)
            }

            totalImported += records.size
            state = updatedState

            if (isComplete) break
            pageToken = nextPageToken
        }

        return totalImported
    }

    private suspend fun <T : Record> processChanges(
        client: HealthConnectClient,
        adapter: HealthRecordAdapter<T>,
        currentState: HealthConnectSyncStateEntity,
        initialToken: String
    ): Int {
        var token: String? = initialToken
        var importedCount = 0

        while (!token.isNullOrBlank()) {
            val changesRes = client.getChanges(token)

            if (changesRes.changesTokenExpired) {
                archiveDao.saveSyncState(
                    currentState.resetAfterChangesTokenExpiry()
                )
                return 0
            }

            val batchMutations = mutableListOf<CloudSyncBatchMutation>()

            for (change in changesRes.changes) {
                when (change) {
                    is UpsertionChange -> {
                        @Suppress("UNCHECKED_CAST")
                        val record = change.record as? T
                        if (record != null && record.metadata.dataOrigin.packageName != "com.health.friday") {
                            val archiveEntity = record.toArchiveEntity(sourceDeviceId, adapter)
                            batchMutations.add(
                                CloudSyncBatchMutation(
                                    entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                                    entityId = archiveEntity.archiveId,
                                    operationType = SyncOperationType.UPSERT
                                ) { hlc ->
                                    val stamped = archiveEntity.copy(syncHlc = hlc)
                                    archiveDao.upsertRecord(stamped)
                                    stamped.toCloudSyncPayload()
                                }
                            )
                            importedCount++
                        }
                    }
                    is DeletionChange -> {
                        val recordId = change.recordId
                        val archiveId = "$sourceDeviceId:${adapter.recordType}:$recordId"
                        batchMutations.add(
                            CloudSyncBatchMutation(
                                entityType = CloudSyncEntityTypes.HEALTH_RECORD,
                                entityId = archiveId,
                                operationType = SyncOperationType.DELETE
                            ) { hlc ->
                                archiveDao.markDeleted(
                                    sourceDeviceId,
                                    adapter.recordType,
                                    recordId,
                                    hlc
                                )
                                null
                            }
                        )
                    }
                }
            }

            val nextToken = changesRes.nextChangesToken
            val nowMs = System.currentTimeMillis()
            val updatedState = currentState.copy(
                changesToken = nextToken,
                lastSyncAtMs = nowMs
            )

            cloudSyncWriter.upsertBatch(batchMutations) {
                archiveDao.saveSyncState(updatedState)
            }

            if (!changesRes.hasMore || nextToken == token) break
            token = nextToken
        }

        return importedCount
    }
}
