package com.health.friday.memory.graph

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryRelationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: MemoryRelationEntity): Long

    @Query("SELECT * FROM memory_relations WHERE subjectFactId = :subjectId")
    suspend fun getRelationsForSubject(subjectId: Long): List<MemoryRelationEntity>

    @Query("SELECT * FROM memory_relations WHERE objectFactId = :objectId")
    suspend fun getRelationsForObject(objectId: Long): List<MemoryRelationEntity>

    @Query("SELECT * FROM memory_relations WHERE subjectFactId = :subjectId OR objectFactId = :subjectId")
    suspend fun getAllRelationsForEntity(subjectId: Long): List<MemoryRelationEntity>

    @Query("SELECT * FROM memory_relations ORDER BY createdAt DESC")
    suspend fun getAllRelations(): List<MemoryRelationEntity>

    @Query("DELETE FROM memory_relations WHERE subjectFactId = :factId OR objectFactId = :factId")
    suspend fun deleteRelationsForFact(factId: Long)
}
