package com.health.friday.ai

import kotlin.coroutines.cancellation.CancellationException

private const val MAX_TOOL_ROUNDS = 3

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

        return try {

            val context =
                AiContext(
                    section = section,
                    summary = contextSummary
                )

            var response =
                aiClient.sendMessage(
                    messages = conversation,
                    context = context
                )

            var rounds = 0

            // The AI may call tools, read the results, and call more tools.
            while (response.toolCalls.isNotEmpty() && rounds < MAX_TOOL_ROUNDS) {

                for (toolCall in response.toolCalls) {

                    conversation.add(
                        AiMessage(
                            role = "tool",
                            content = runTool(toolCall)
                        )
                    )
                }

                rounds++

                response =
                    aiClient.sendMessage(
                        messages = conversation,
                        context = context
                    )
            }

            val reply =
                if (response.toolCalls.isNotEmpty()) {
                    "I stopped after $MAX_TOOL_ROUNDS tool steps. " +
                            "Check the Nutrition tab to see what was saved."
                } else {
                    response.message
                }

            conversation.add(
                AiMessage(
                    role = "assistant",
                    content = reply
                )
            )

            reply

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Something went wrong: ${e.message ?: "unknown error"}"
        }
    }

    private suspend fun runTool(
        toolCall: AiToolCall
    ): String {

        val tool =
            toolRegistry.getTool(toolCall.name)
                ?: return "Unknown tool: ${toolCall.name}"

        return try {
            tool.execute(toolCall.arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Tool ${toolCall.name} failed: ${e.message ?: "unknown error"}"
        }
    }

    fun clearConversation() {
        conversation.clear()
    }
}