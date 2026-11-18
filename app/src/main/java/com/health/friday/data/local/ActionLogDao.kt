package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionLogEntity): Long

    @Query("SELECT * FROM action_log ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAction(): ActionLogEntity?

    // The most recent action that has a defined way to be reversed.
    @Query(
        "SELECT * FROM action_log " +
                "WHERE inverseTool != '' " +
                "ORDER BY timestamp DESC, id DESC LIMIT 1"
    )
    suspend fun getLastUndoableAction(): ActionLogEntity?

    @Query("SELECT * FROM action_log ORDER BY timestamp DESC LIMIT 50")
    fun getRecentActionsFlow(): Flow<List<ActionLogEntity>>

    @Query("DELETE FROM action_log WHERE id = :actionId")
    suspend fun deleteAction(actionId: Long)

    @Query("DELETE FROM action_log WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM action_log")
    suspend fun clearAll()
}
