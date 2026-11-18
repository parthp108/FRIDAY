package com.health.friday.device

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.time.TimeRangeFilter
import com.health.friday.data.local.HealthConnectArchiveDao
import com.health.friday.data.local.HealthConnectSyncStateEntity
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.SyncOperationType
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncBatchMutation
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

private data class RecordSyncTarget<T : Record>(
    val kclass: KClass<T>,
    val adapter: HealthRecordAdapter<T>
)

class HealthConnectArchiveSyncer(
    private val healthConnectRepo: HealthConnectRepository,
    private val archiveDao: HealthConnectArchiveDao,
    private val cloudSyncWriter: AndroidCloudSyncWriter,
    private val sourceDeviceId: String
) {

    suspend fun syncAllRecordTypes(): Map<String, Int> {
        val results = mutableMapOf<String, Int>()

        if (!healthConnectRepo.isAvailable()) return results

        val targets: List<RecordSyncTarget<out Record>> = listOf(
            RecordSyncTarget(StepsRecord::class, StepsRecordAdapter),
            RecordSyncTarget(HeartRateRecord::class, HeartRateRecordAdapter),
            RecordSyncTarget(SleepSessionRecord::class, SleepSessionRecordAdapter),
            RecordSyncTarget(WeightRecord::class, WeightRecordAdapter)
        )

        val granted = healthConnectRepo.getGrantedPermissions()

        for (target in targets) {
            val readPerm = HealthPermission.getReadPermission(target.kclass)
            if (readPerm in granted) {
                try {
                    val count = syncTarget(target)
                    results[target.adapter.recordType] = count
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Log per-target failure without blocking other independent permitted types
                }
            }
        }

        return results
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
            val token = try {
                client.getChangesToken(ChangesTokenRequest(recordTypes = setOf(kclass)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

            if (token.isNullOrBlank()) {
                // Do not proceed with baseline import without a valid Changes Token
                return 0
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
        val baselineToMs = initialState.baselineStartedAtMs ?: System.currentTimeMillis()

        val hasHistoryPermission = healthConnectRepo.supportsHistoryRead() &&
                healthConnectRepo.hasHistoryReadAccess()

        val baselineFromMs = initialState.baselineFromEpochMs ?: if (hasHistoryPermission) {
            Instant.EPOCH.toEpochMilli()
        } else {
            baselineToMs - Duration.ofDays(30).toMillis()
        }

        val startTime = Instant.ofEpochMilli(baselineFromMs)
        val endTime = Instant.ofEpochMilli(baselineToMs)

        var pageToken = initialState.baselinePageToken
        var totalImported = 0

        while (true) {
            val req = ReadRecordsRequest(
                recordType = kclass,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                pageToken = pageToken,
                pageSize = 200
            )

            val res = try {
                client.readRecords(req)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }

            val records = res.records.filter { it.metadata.dataOrigin.packageName != "com.health.friday" }

            val batchMutations = mutableListOf<CloudSyncBatchMutation>()

            for (record in records) {
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
            }

            val nextPageToken = res.pageToken
            val isComplete = nextPageToken.isNullOrBlank()
            val nowMs = System.currentTimeMillis()

            val updatedState = initialState.copy(
                baselineFromEpochMs = baselineFromMs,
                baselineStartedAtMs = baselineToMs,
                baselinePageToken = if (isComplete) null else nextPageToken,
                baselineComplete = isComplete,
                baselineCompletedAtMs = if (isComplete) nowMs else null,
                lastSyncAtMs = nowMs
            )

            cloudSyncWriter.upsertBatch(batchMutations) {
                archiveDao.saveSyncState(updatedState)
            }

            totalImported += records.size

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
            val changesRes: ChangesResponse = try {
                client.getChanges(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e
            }

            if (changesRes.changesTokenExpired) {
                val resetState = currentState.copy(
                    changesToken = null,
                    baselineComplete = false,
                    baselinePageToken = null
                )
                archiveDao.saveSyncState(resetState)
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
