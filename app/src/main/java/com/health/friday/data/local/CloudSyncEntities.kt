package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cloud_sync_outbox",
    indices = [
        Index(value = ["status", "createdAtMs"])
    ]
)
data class CloudSyncOutboxEntity(
    @PrimaryKey
    val operationId: String,

    val originDeviceId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val hlc: String,
    val createdAtMs: Long,
    val schemaVersion: Int,
    val payloadJson: String?,

    // PENDING or UPLOADED
    @ColumnInfo(defaultValue = "'PENDING'")
    val status: String = "PENDING",

    val batchId: String? = null,
    val uploadedAtMs: Long? = null
)

@Entity(tableName = "cloud_sync_inbox")
data class CloudSyncInboxEntity(
    @PrimaryKey
    val operationId: String,

    val batchId: String,
    val originDeviceId: String,
    val processedAtMs: Long
)

@Entity(tableName = "cloud_sync_cursor")
data class CloudSyncCursorEntity(
    @PrimaryKey
    val id: Int = 1,

    val drivePageToken: String? = null,
    val lastSuccessfulSyncAtMs: Long? = null
)

@Entity(tableName = "cloud_sync_clock")
data class CloudSyncClockEntity(
    @PrimaryKey
    val id: Int = 1,

    val nodeId: String,
    val lastWallMs: Long,
    val lastCounter: Int
)

@Entity(tableName = "cloud_sync_backfill_state")
data class CloudSyncBackfillStateEntity(
    @PrimaryKey
    val stage: String,

    val completedAtMs: Long
)
