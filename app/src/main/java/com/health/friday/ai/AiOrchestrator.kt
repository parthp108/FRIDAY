package com.health.friday.ai

class AiOrchestrator(
    private val aiClient: AiClient,
    private val toolRegistry: AiToolRegistry
) {

    private val conversation =
        mutableListOf<AiMessage>()

    suspend fun sendMessage(
        message: String,
        section: String = "global",
        contextSummary: String = ""
    ): String {

        conversation.add(
            AiMessage(
                role = "user",
                content = message
            )
        )

        val context =
            AiContext(
                section = section,
                summary = contextSummary
            )

        val response =
            aiClient.sendMessage(
                messages = conversation,
                context = context
            )

        for (toolCall in response.toolCalls) {

            val tool =
                toolRegistry.getTool(toolCall.name)
                    ?: continue

            val toolResult =
                tool.execute(
                    toolCall.arguments
                )

            conversation.add(
                AiMessage(
                    role = "tool",
                    content = toolResult
                )
            )
        }

        conversation.add(
            AiMessage(
                role = "assistant",
                content = response.message
            )
        )

        return response.message
    }

    fun clearConversation() {
        conversation.clear()
    }
}