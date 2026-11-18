package com.health.friday.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole { USER, AI, ASSISTANT, SYSTEM, TOOL }

@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String,
    val timestamp: Long = 0L,
    val startTime: Long = 0L,
    val lastUpdated: Long = 0L,
    val isArchived: Boolean = false,
    val messages: List<ChatMessageDto> = emptyList()
)

@Serializable
data class ChatMessageDto(
    val id: String = "",
    val sender: ChatRole = ChatRole.USER,
    val text: String = "",
    val timestamp: Long = 0L,
    val isStreaming: Boolean = false,
    val sessionId: String = "",
    val metadata: Map<String, String> = emptyMap()
)
