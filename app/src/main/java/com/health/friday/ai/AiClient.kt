package com.health.friday.ai

interface AiClient {

    suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse
}