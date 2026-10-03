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

    suspend fun generateJournalFeedback(
        journalText: String
    ): String {

        val cleanText =
            journalText.trim()

        if (cleanText.isEmpty()) {
            throw IllegalArgumentException("Journal entry is empty")
        }

        val request =
            """
        Read the following journal entry and respond to the person like a good friend.

        The response should:
        - be short, around 1 to 3 sentences
        - show that you understood what they wrote
        - sound natural and conversational
        - acknowledge what they are feeling or describing when appropriate
        - give a small practical thought only when it genuinely helps
        - never sound like a therapist, motivational speaker, or formal AI
        - do not diagnose anything
        - do not invent details that are not in the entry
        - do not force advice when the person is simply sharing something
        - do not use emojis
        - do not start with a greeting
        - do not mention that you are an AI
        - return only the response that should appear under the journal entry

        Journal entry:
        $cleanText
        """.trimIndent()

        val response =
            aiClient.sendMessage(
                messages = listOf(
                    AiMessage(
                        role = "user",
                        content = request
                    )
                ),
                context = AiContext(
                    section = "journal"
                )
            )

        if (response.toolCalls.isNotEmpty()) {
            throw IllegalStateException(
                "Journal feedback unexpectedly requested an app tool"
            )
        }

        val feedback =
            response.message.trim()

        if (feedback.isEmpty()) {
            throw IllegalStateException(
                "AI returned empty journal feedback"
            )
        }

        return feedback
    }

    fun restoreConversation(
        messages: List<AiMessage>
    ) {
        conversation.clear()
        conversation.addAll(messages)
    }

    fun clearConversation() {
        conversation.clear()
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


}
