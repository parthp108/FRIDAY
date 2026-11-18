package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_record_archive",
    indices = [
        Index(
            value = [
                "sourceDeviceId",
                "recordType",
                "recordId"
            ],
            unique = true
        ),
        Index(
            value = [
                "recordType",
                "startTimeMs"
            ]
        )
    ]
)
data class HealthRecordArchiveEntity(
    @PrimaryKey
    val archiveId: String,

    val sourceDeviceId: String,
    val recordType: String,
    val recordId: String,

    val dataOriginPackage: String,

    val clientRecordId: String? = null,
    val clientRecordVersion: Long = 0L,

    val lastModifiedTimeMs: Long,
    val recordingMethod: Int,

    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceType: Int? = null,

    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,

    // Canonical JSON for the individual Health Connect record.
    val payloadJson: String,

    val syncHlc: String? = null,

    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false
)

@Entity(tableName = "health_connect_sync_state")
data class HealthConnectSyncStateEntity(
    @PrimaryKey
    val recordType: String,

    // Differential Changes API checkpoint.
    val changesToken: String? = null,

    // Initial historical import checkpoint.
    val baselinePageToken: String? = null,
    val baselineFromEpochMs: Long? = null,
    val baselineStartedAtMs: Long? = null,
    val baselineCompletedAtMs: Long? = null,

    @ColumnInfo(defaultValue = "0")
    val baselineComplete: Boolean = false,

    val lastSyncAtMs: Long? = null
)
