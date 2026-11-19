package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT syncId FROM todo_items WHERE syncHlc IS NULL ORDER BY createdAt ASC")
    suspend fun getUnsyncedSyncIds(): List<String>

    @Query("SELECT * FROM todo_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): TodoItem?

    @Query("DELETE FROM todo_items WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Insert
    suspend fun insert(item: TodoItem): Long

    @Update
    suspend fun update(item: TodoItem)

    @Delete
    suspend fun delete(item: TodoItem)

    @Query("SELECT * FROM todo_items ORDER BY isDone ASC, createdAt DESC")
    fun getAll(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TodoItem?
}

@Dao
interface GoalDao {

    @Query("SELECT syncId FROM goals WHERE syncHlc IS NULL ORDER BY createdAt ASC")
    suspend fun getUnsyncedSyncIds(): List<String>

    @Query("SELECT * FROM goals WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Goal?

    @Query("DELETE FROM goals WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goals ORDER BY isDone ASC, createdAt DESC")
    fun getAll(): Flow<List<Goal>>
}

@Dao
interface JournalDao {

    @Query("SELECT syncId FROM journal_entries WHERE syncHlc IS NULL ORDER BY createdAt ASC")
    suspend fun getUnsyncedSyncIds(): List<String>

    @Query("SELECT * FROM journal_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): JournalEntry?

    @Query("DELETE FROM journal_entries WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Insert
    suspend fun insert(entry: JournalEntry): Long

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    suspend fun getAllNow(): List<JournalEntry>
}