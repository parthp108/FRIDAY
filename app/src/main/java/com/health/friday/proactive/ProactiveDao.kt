package com.health.friday.proactive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProactiveDao {
    @Insert
    suspend fun insertNudge(nudge: NudgeHistoryEntity): Long

    @Update
    suspend fun updateNudge(nudge: NudgeHistoryEntity)

    @Query("SELECT * FROM nudge_history WHERE id = :id LIMIT 1")
    suspend fun getNudgeById(id: Long): NudgeHistoryEntity?

    @Query("SELECT * FROM nudge_history WHERE timestamp >= :since")
    suspend fun getNudgesSince(since: Long): List<NudgeHistoryEntity>

    @Query("SELECT * FROM nudge_history WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastNudgeOfType(type: String): NudgeHistoryEntity?

    @Query("SELECT * FROM nudge_history WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNudgesOfType(type: String, limit: Int): List<NudgeHistoryEntity>

    @Insert
    suspend fun insertProtocol(protocol: ProtocolEntity): Long

    @Query("SELECT * FROM protocols WHERE isEnabled = 1")
    suspend fun getActiveProtocols(): List<ProtocolEntity>

    @Query("SELECT * FROM protocols WHERE id = :id LIMIT 1")
    suspend fun getProtocolById(id: Long): ProtocolEntity?

    @Query("SELECT COUNT(*) FROM protocols")
    suspend fun getProtocolCount(): Int
}
