package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_conversations"
)
data class ChatConversation(


    @PrimaryKey(autoGenerate = true)
val id: Long = 0,

val title: String,

val createdAt: Long = System.currentTimeMillis(),

val updatedAt: Long = System.currentTimeMillis()


)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId")
    ]
)
data class ChatMessage(


    @PrimaryKey(autoGenerate = true)
val id: Long = 0,

val conversationId: Long,

val role: String,

val content: String,

val createdAt: Long = System.currentTimeMillis()


)
