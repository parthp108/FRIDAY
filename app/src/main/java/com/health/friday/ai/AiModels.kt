
package com.health.friday.ai

data class AiMessage(
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class AiContext(
    val section: String = "global",
    val summary: String = ""
)

data class AiResponse(
    val message: String,
    val toolCalls: List<AiToolCall> = emptyList()
)

data class AiToolCall(
    val name: String,
    val arguments: Map<String, String> = emptyMap()
)

