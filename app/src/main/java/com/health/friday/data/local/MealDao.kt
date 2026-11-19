package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Query("SELECT syncId FROM meals WHERE syncHlc IS NULL ORDER BY timestamp ASC")
    suspend fun getUnsyncedSyncIds(): List<String>

    @Query("SELECT * FROM meals WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Meal?

    @Query("DELETE FROM meals WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Insert
    suspend fun insertMeal(meal: Meal): Long

    @Delete
    suspend fun deleteMeal(meal: Meal)

    @Update
    suspend fun updateMeal(meal: Meal)

    @Query("SELECT * FROM meals WHERE id IN (:ids)")
    suspend fun getMealsByIds(ids: List<Long>): List<Meal>

    @Query("SELECT * FROM meals ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<Meal>>

    @Query("""
        SELECT * FROM meals
        WHERE timestamp >= :startOfDay
        AND timestamp < :endOfDay
        ORDER BY timestamp DESC
    """)
    fun getMealsForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<Meal>>

    @Query("SELECT * FROM meals WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getMealsBetween(start: Long, end: Long): List<Meal>
}
