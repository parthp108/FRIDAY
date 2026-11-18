package com.health.friday.nutrition.calibration

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodCalibrationDao {

    @Query("SELECT * FROM food_calibration WHERE canonicalName = :name LIMIT 1")
    suspend fun getCalibration(name: String): FoodCalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(calibration: FoodCalibrationEntity)

    @Query("DELETE FROM food_calibration WHERE canonicalName = :name")
    suspend fun deleteCalibration(name: String)
}
