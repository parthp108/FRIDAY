
package com.health.friday.chat

import com.health.friday.data.local.ChatConversation
import com.health.friday.data.local.ChatConversationDao
import com.health.friday.data.local.ChatMessage
import com.health.friday.data.local.ChatMessageDao
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val conversationDao: ChatConversationDao,
    private val messageDao: ChatMessageDao
) {

    fun getConversations(): Flow<List<ChatConversation>> {
        return conversationDao.getAll()
    }

    suspend fun saveConversation(
        title: String,
        messages: List<ChatMessage>,
        conversationId: Long? = null
    ): ChatConversation? {

        val visibleMessages =
            messages.filter {
                it.role == "user" ||
                        it.role == "assistant"
            }

        if (visibleMessages.isEmpty()) {
            return null
        }

        val cleanTitle =
            title.trim().ifEmpty {
                "FRIDAY conversation"
            }

        val now =
            System.currentTimeMillis()

        if (conversationId != null) {

            val existingConversation =
                conversationDao.getById(conversationId)
                    ?: return null

            messageDao.deleteForConversation(
                conversationId
            )

            val storedMessages =
                visibleMessages.map {
                    it.copy(
                        id = 0,
                        conversationId = conversationId
                    )
                }

            messageDao.insertAll(storedMessages)

            val updatedConversation =
                existingConversation.copy(
                    title = cleanTitle,
                    updatedAt = now
                )

            conversationDao.update(
                updatedConversation
            )

            return updatedConversation
        }

        val conversation =
            ChatConversation(
                title = cleanTitle,
                createdAt = now,
                updatedAt = now
            )

        val newConversationId =
            conversationDao.insert(conversation)

        val storedMessages =
            visibleMessages.map {
                it.copy(
                    id = 0,
                    conversationId = newConversationId
                )
            }

        messageDao.insertAll(storedMessages)

        return conversation.copy(
            id = newConversationId
        )
    }

    suspend fun getMessages(
        conversationId: Long
    ): List<ChatMessage> {
        return messageDao.getForConversation(
            conversationId
        )
    }

    suspend fun deleteConversation(
        conversation: ChatConversation
    ) {
        conversationDao.delete(conversation)
    }
}

