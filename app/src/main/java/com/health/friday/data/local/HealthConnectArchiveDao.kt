package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthConnectArchiveDao {

    // ---------------- RECORD ARCHIVE ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: HealthRecordArchiveEntity)

    @Query("SELECT archiveId FROM health_record_archive WHERE syncHlc IS NULL AND isDeleted = 0 ORDER BY startTimeMs ASC")
    suspend fun getUnsyncedArchiveIds(): List<String>

    @Query(
        """
        SELECT * FROM health_record_archive
        WHERE archiveId = :archiveId
        LIMIT 1
        """
    )
    suspend fun getByArchiveId(archiveId: String): HealthRecordArchiveEntity?

    @Query(
        """
        SELECT * FROM health_record_archive
        WHERE sourceDeviceId = :sourceDeviceId
          AND recordType = :recordType
          AND recordId = :recordId
        LIMIT 1
        """
    )
    suspend fun getRecord(
        sourceDeviceId: String,
        recordType: String,
        recordId: String
    ): HealthRecordArchiveEntity?

    @Query(
        """
        SELECT * FROM health_record_archive
        WHERE recordType = :recordType
          AND isDeleted = 0
        ORDER BY startTimeMs ASC
        LIMIT :limit
        """
    )
    suspend fun getRecords(
        recordType: String,
        limit: Int
    ): List<HealthRecordArchiveEntity>

    @Query(
        """
        UPDATE health_record_archive
        SET isDeleted = 1,
            syncHlc = :syncHlc
        WHERE archiveId = :archiveId
        """
    )
    suspend fun markDeletedByArchiveId(
        archiveId: String,
        syncHlc: String
    ): Int

    @Query(
        """
        UPDATE health_record_archive
        SET isDeleted = 1,
            syncHlc = :syncHlc
        WHERE sourceDeviceId = :sourceDeviceId
          AND recordType = :recordType
          AND recordId = :recordId
        """
    )
    suspend fun markDeleted(
        sourceDeviceId: String,
        recordType: String,
        recordId: String,
        syncHlc: String
    ): Int

    // ---------------- PER-TYPE SYNC STATE ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: HealthConnectSyncStateEntity)

    @Query(
        """
        SELECT * FROM health_connect_sync_state
        WHERE recordType = :recordType
        LIMIT 1
        """
    )
    suspend fun getSyncState(recordType: String): HealthConnectSyncStateEntity?

    @Query("SELECT * FROM health_connect_sync_state ORDER BY recordType ASC")
    suspend fun getAllSyncStates(): List<HealthConnectSyncStateEntity>
}
