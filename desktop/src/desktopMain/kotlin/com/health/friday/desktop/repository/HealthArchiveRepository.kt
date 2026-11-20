package com.health.friday.desktop.repository

import com.health.friday.shared.sync.cloud.HealthRecordSyncPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A UI-safe projection of an archived Health Connect record. */
data class HealthArchiveRecordSummary(
    val archiveId: String,
    val recordType: String,
    val dataOriginPackage: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val lastModifiedTimeMs: Long
)

class HealthArchiveRepository {
    private val _records = MutableStateFlow<List<HealthArchiveRecordSummary>>(emptyList())
    val records: StateFlow<List<HealthArchiveRecordSummary>> = _records

    fun setCloudRecords(records: List<HealthRecordSyncPayload>) {
        _records.value = records.map { record ->
            HealthArchiveRecordSummary(
                archiveId = record.archiveId,
                recordType = record.recordType,
                dataOriginPackage = record.dataOriginPackage,
                startTimeMs = record.startTimeMs,
                endTimeMs = record.endTimeMs,
                lastModifiedTimeMs = record.lastModifiedTimeMs
            )
        }.sortedByDescending { it.startTimeMs ?: it.endTimeMs ?: it.lastModifiedTimeMs }
    }
}
