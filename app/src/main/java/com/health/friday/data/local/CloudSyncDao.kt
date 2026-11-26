package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CloudSyncDao {

    // ---------------- OUTBOX ----------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutbox(operation: CloudSyncOutboxEntity): Long

    @Query(
        """
        SELECT * FROM cloud_sync_outbox
        WHERE status = 'PENDING'
        ORDER BY createdAtMs ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingOutbox(limit: Int): List<CloudSyncOutboxEntity>

    @Query(
        """
        UPDATE cloud_sync_outbox
        SET status = 'UPLOADED',
            batchId = :batchId,
            uploadedAtMs = :uploadedAtMs
        WHERE operationId IN (:operationIds)
        """
    )
    suspend fun markUploaded(
        operationIds: List<String>,
        batchId: String,
        uploadedAtMs: Long
    )

    @Query(
        """
        SELECT COUNT(*) FROM cloud_sync_outbox
        WHERE status = 'PENDING'
        """
    )
    suspend fun pendingCount(): Int

    // ---------------- INBOX ----------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInbox(operation: CloudSyncInboxEntity): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM cloud_sync_inbox
            WHERE operationId = :operationId
        )
        """
    )
    suspend fun hasProcessed(operationId: String): Boolean

    // ---------------- CURSOR ----------------

    @Query(
        """
        SELECT * FROM cloud_sync_cursor
        WHERE id = 1
        LIMIT 1
        """
    )
    suspend fun getCursor(): CloudSyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(cursor: CloudSyncCursorEntity)

    // ---------------- CLOCK ----------------

    @Query(
        """
        SELECT * FROM cloud_sync_clock
        WHERE id = 1
        LIMIT 1
        """
    )
    suspend fun getClockState(): CloudSyncClockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClockState(state: CloudSyncClockEntity)

    // ---------------- PER-ENTITY VERSION / TOMBSTONE ----------------

    @Query(
        """
        SELECT * FROM cloud_sync_entity_state
        WHERE entityType = :entityType AND entityId = :entityId
        LIMIT 1
        """
    )
    suspend fun getEntityState(
        entityType: String,
        entityId: String
    ): CloudSyncEntityStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntityState(state: CloudSyncEntityStateEntity)

    // ---------------- MAINTENANCE ----------------

    @Query(
        """
        DELETE FROM cloud_sync_outbox
        WHERE status = 'UPLOADED'
        AND operationType != 'DELETE'
        AND uploadedAtMs < :olderThanMs
        """
    )
    suspend fun deleteOldUploadedOperations(olderThanMs: Long): Int

    @Query(
        """
        DELETE FROM cloud_sync_inbox
        WHERE processedAtMs < :olderThanMs
        """
    )
    suspend fun deleteOldInboxEntries(olderThanMs: Long): Int

    // ---------------- LEGACY BACKFILL PROGRESS ----------------

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM cloud_sync_backfill_state
            WHERE stage = :stage
        )
        """
    )
    suspend fun isBackfillStageComplete(stage: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBackfillStage(
        state: CloudSyncBackfillStateEntity
    )
}
