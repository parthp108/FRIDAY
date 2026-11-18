package com.health.friday.shared.ai

import com.health.friday.shared.models.ChatMessageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface SharedTool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, String>): String
}

class PromptFirewall {
    private val blockedPatterns = listOf(
        Regex("(?i)ignore previous instructions"),
        Regex("(?i)system prompt dump"),
        Regex("(?i)reveal secret key")
    )

    fun sanitizeInput(text: String): String {
        var cleanText = text
        for (pattern in blockedPatterns) {
            cleanText = cleanText.replace(pattern, "[FILTERED]")
        }
        return cleanText
    }
}

class SharedAiOrchestrator(
    private val registeredTools: MutableList<SharedTool> = mutableListOf()
) {
    private val firewall = PromptFirewall()

    fun registerTool(tool: SharedTool) {
        registeredTools.add(tool)
    }

    fun getAvailableTools(): List<SharedTool> = registeredTools.toList()

    fun buildSystemPrompt(userContext: String = ""): String {
        val toolDesc = if (registeredTools.isNotEmpty()) {
            "Available Tools:\n" + registeredTools.joinToString("\n") { "- ${it.name}: ${it.description}" }
        } else ""

        return """
            You are a helpful, factual, and direct AI assistant.
            Answer the user's query accurately using facts.
            Do not invent fake characters, fictional facts, or roleplay.
            
            $toolDesc
            
            $userContext
        """.trimIndent()
    }

    suspend fun processMessage(
        userText: String,
        history: List<ChatMessageDto>,
        userContext: String = "",
        modelResponder: suspend (systemPrompt: String, userText: String) -> String
    ): Flow<String> = flow {
        val sanitized = firewall.sanitizeInput(userText)
        val systemPrompt = buildSystemPrompt(userContext)
        emit("Thinking...")
        
        val rawResponse = modelResponder(systemPrompt, sanitized)
        emit(rawResponse)
    }
}
