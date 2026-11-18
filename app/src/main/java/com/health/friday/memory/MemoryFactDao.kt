package com.health.friday.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryFactDao {
    @Insert
    suspend fun insertFact(fact: MemoryFactEntity): Long

    @Delete
    suspend fun deleteFact(fact: MemoryFactEntity)

    @Query("SELECT * FROM memory_facts ORDER BY createdAt ASC")
    suspend fun getAllFacts(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY createdAt ASC")
    fun observeFacts(): Flow<List<MemoryFactEntity>>
}