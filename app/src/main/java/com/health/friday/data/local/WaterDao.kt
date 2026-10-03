package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {

    @Insert
    suspend fun insertWater(entry: WaterEntry): Long

    @Delete
    suspend fun deleteWater(entry: WaterEntry)

    @Query("SELECT * FROM water_entries ORDER BY timestamp DESC")
    fun getAllWater(): Flow<List<WaterEntry>>

    @Query("""
        SELECT *
        FROM water_entries
        WHERE timestamp >= :startOfDay
        AND timestamp < :endOfDay
        ORDER BY timestamp DESC
    """)
    fun getEntriesForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<WaterEntry>>

    @Query("""
        SELECT COALESCE(SUM(amountMl), 0)
        FROM water_entries
        WHERE timestamp >= :startOfDay
        AND timestamp < :endOfDay
    """)
    fun getTodayWater(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<Int>
}