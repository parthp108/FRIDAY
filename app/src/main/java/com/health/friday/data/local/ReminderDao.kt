
package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query(
        "SELECT * FROM reminders " +
                "WHERE enabled = 1 " +
                "ORDER BY timeMillis ASC"
    )
    fun getActive(): Flow<List<Reminder>>

    @Query(
        "SELECT * FROM reminders " +
                "ORDER BY timeMillis ASC"
    )
    fun getAll(): Flow<List<Reminder>>

    @Query(
        "SELECT * FROM reminders " +
                "WHERE id = :id"
    )
    suspend fun getById(id: Long): Reminder?

    @Query(
        "UPDATE reminders " +
                "SET enabled = 0 " +
                "WHERE id = :id"
    )
    suspend fun disable(id: Long)
}

