package com.health.friday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Durable last-writer-wins metadata for every cloud-synced entity.
 *
 * This row must survive physical deletion of the underlying record. Otherwise,
 * an older UPSERT arriving after a DELETE could recreate the deleted record.
 */
@Entity(
    tableName = "cloud_sync_entity_state",
    primaryKeys = ["entityType", "entityId"]
)
data class CloudSyncEntityStateEntity(
    val entityType: String,
    val entityId: String,
    val lastHlc: String,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean,
    val updatedAtMs: Long
)
