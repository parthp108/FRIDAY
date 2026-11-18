package com.health.friday.desktop.ai

import com.health.friday.shared.ai.LocalOllamaClient
import com.health.friday.shared.ai.SharedAiOrchestrator

class DesktopAiClient(
    private val orchestrator: SharedAiOrchestrator,
    private val ollamaClient: LocalOllamaClient = LocalOllamaClient()
) {
    private companion object {
        val GREETING = Regex("""\b(hey|hello|hi)\b""")
    }

    suspend fun generateStreamingResponse(
        userPrompt: String,
        apiKey: String,
        personaStyle: String,
        localModelName: String,
        onToken: (String) -> Unit
    ): Boolean {
        val systemPrompt = orchestrator.buildSystemPrompt("Persona: $personaStyle")
        return ollamaClient.generateStreamingChatResponse(systemPrompt, userPrompt, localModelName, onToken)
    }

    suspend fun generateResponse(
        userPrompt: String,
        apiKey: String,
        personaStyle: String,
        localModelName: String
    ): String {
        val systemPrompt = orchestrator.buildSystemPrompt("Persona: $personaStyle")

        // Strict 100% local model execution via Ollama
        val ollamaReply = ollamaClient.generateChatResponse(systemPrompt, userPrompt, localModelName)
        if (!ollamaReply.isNullOrBlank()) return ollamaReply

        return generateFallbackResponse(userPrompt, personaStyle)
    }

    fun generateFallbackResponse(input: String, persona: String): String {
        val lower = input.lowercase().trim()
        return when {
            GREETING.containsMatchIn(lower) -> when (persona) {
                "Casual Friend" -> "Hey! I'm online and ready. What are we working on today?"
                "Strict Productivity Coach" -> "Hello. Let's stay focused today. Review your tasks and let's get to work."
                else -> "Hello Boss. F.R.I.D.A.Y. online. How may I assist you?"
            }
            lower.contains("who are you") -> "I am F.R.I.D.A.Y., your personal AI assistant."
            else -> "I couldn't reach your local Ollama model. Please make sure Ollama is running on localhost:11434."
        }
    }
}
