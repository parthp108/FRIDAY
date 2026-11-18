package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySignalsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(signal: DailySignalsEntity)

    @Query("SELECT * FROM daily_signals WHERE date = :date LIMIT 1")
    suspend fun getSignalForDate(date: String): DailySignalsEntity?

    @Query("SELECT * FROM daily_signals WHERE date = :date LIMIT 1")
    fun observeSignalForDate(date: String): Flow<DailySignalsEntity?>

    @Query("SELECT * FROM daily_signals ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSignals(limit: Int): List<DailySignalsEntity>

    @Query("SELECT * FROM daily_signals ORDER BY date DESC LIMIT 14")
    fun getRecentSignalsFlow(): Flow<List<DailySignalsEntity>>

    @Query("SELECT * FROM daily_signals WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getSignalsInRange(startDate: String, endDate: String): List<DailySignalsEntity>

    @Query("UPDATE daily_signals SET moodScore = :score WHERE date = :date")
    suspend fun updateMood(date: String, score: Int): Int
}