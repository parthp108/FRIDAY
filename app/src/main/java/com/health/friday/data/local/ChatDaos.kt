
package com.health.friday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatConversationDao {

    @Insert
    suspend fun insert(
        conversation: ChatConversation
    ): Long

    @Update
    suspend fun update(
        conversation: ChatConversation
    )

    @Delete
    suspend fun delete(
        conversation: ChatConversation
    )

    @Query(
        "SELECT * FROM chat_conversations " +
                "WHERE id = :conversationId"
    )
    suspend fun getById(
        conversationId: Long
    ): ChatConversation?

    @Query(
        "SELECT * FROM chat_conversations " +
                "ORDER BY updatedAt DESC"
    )
    fun getAll(): Flow<List<ChatConversation>>
}

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(
        message: ChatMessage
    ): Long

    @Insert
    suspend fun insertAll(
        messages: List<ChatMessage>
    )

    @Query(
        "SELECT * FROM chat_messages " +
                "WHERE conversationId = :conversationId " +
                "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getForConversation(
        conversationId: Long
    ): List<ChatMessage>

    @Query(
        "DELETE FROM chat_messages " +
                "WHERE conversationId = :conversationId"
    )
    suspend fun deleteForConversation(
        conversationId: Long
    )
}

