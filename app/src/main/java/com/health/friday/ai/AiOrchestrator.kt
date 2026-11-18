package com.health.friday.ai

import com.health.friday.actions.ActionRecorder
import com.health.friday.chat.ChatRepository
import com.health.friday.data.local.ChatMessage
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.domain.SignalsBuilder
import com.health.friday.memory.MemoryFactDao
import com.health.friday.memory.graph.GraphManager
import com.health.friday.memory.graph.MemoryRetriever
import com.health.friday.memory.rag.FridayEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_TOOL_ROUNDS = 5
private const val MAX_TOOL_CALLS_PER_RESPONSE = 8

class AiOrchestrator(
    private val aiClient: AiClient,
    private val toolRegistry: AiToolRegistry,
    private val memoryFactDao: MemoryFactDao,
    private val actionRecorder: ActionRecorder,
    private val signalsBuilder: SignalsBuilder? = null,
    private val signalsDao: DailySignalsDao? = null,
    private val chatRepository: ChatRepository? = null,
    private val graphManager: GraphManager? = null,
    private val embedder: FridayEmbedder? = null
) {
    private val conversation = mutableListOf<AiMessage>()
    private val narrationGuard = NarrationGuard()

    suspend fun sendMessage(
        message: String,
        section: String = "global",
        contextSummary: String = "",
        onPartialResponse: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.Default) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return@withContext "Enter a message first."

        conversation.add(AiMessage(role = "user", content = cleanMessage))

        try {
            // 1. Perform Subconscious Associative Recall & Build Living Ambient Prompt
            val livingPrompt = buildSubconsciousContext(cleanMessage, contextSummary)

            val context = AiContext(section = section, summary = livingPrompt)

            var response = aiClient.sendMessage(conversation.toList(), context, onPartialResponse)
            var rounds = 0
            var totalToolCalls = 0

            val executed = mutableSetOf<String>()

            // 2. Process tool calls loop
            while (response.toolCalls.isNotEmpty() && rounds < MAX_TOOL_ROUNDS) {
                for (call in response.toolCalls) {
                    val signature = call.name + "|" + call.arguments.toSortedMap()
                    val result = when {
                        totalToolCalls >= MAX_TOOL_CALLS_PER_RESPONSE ->
                            "SKIPPED: Tool call limit reached. Nothing was executed."
                        !executed.add(signature) ->
                            "SKIPPED: Identical call already ran this turn. Nothing was executed again."
                        else -> {
                            totalToolCalls++
                            runTool(call)
                        }
                    }
                    conversation.add(
                        AiMessage(
                            role = "tool",
                            content = "Tool '${call.name}' succeeded: $result. Now reply to the user naturally in character."
                        )
                    )
                }

                rounds++
                response = aiClient.sendMessage(conversation.toList(), context, onPartialResponse)
            }

            // 3. Handle pure conversational text vs tool follow-up voice response
            var reply = when {
                response.message.isNotBlank() -> response.message.replace("```json", "").replace("```", "").trim()
                response.toolCalls.isNotEmpty() -> toolResultsFallback()
                conversation.lastOrNull()?.role == "tool" -> toolResultsFallback()
                else -> "I couldn't produce a reliable response. No additional action was taken."
            }

            // Execute Narration Guard
            reply = narrationGuard.guardResponse(reply, conversation.toList())

            conversation.add(AiMessage(role = "assistant", content = reply))

            if (chatRepository != null) {
                runCatching {
                    val chatMsgs = conversation.map {
                        ChatMessage(
                            conversationId = 0,
                            role = it.role,
                            content = it.content
                        )
                    }
                    chatRepository.saveConversation("FRIDAY Proactive Voice", chatMsgs)
                }
            }

            reply
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = "FRIDAY AI error: ${e.message ?: e.javaClass.simpleName}"
            conversation.add(AiMessage(role = "assistant", content = error))
            error
        }
    }

    private suspend fun buildSubconsciousContext(userMessage: String, contextSummary: String): String {
        var sleepHours: Float? = null
        var waterMl = 0
        var kcalToday = 0

        val sb = signalsBuilder
        val sd = signalsDao
        if (sb != null && sd != null) {
            runCatching {
                val now = System.currentTimeMillis()
                sb.buildSignalsForDate(now)

                val zone = ZoneId.systemDefault()
                val todayStr = LocalDate.now(zone).toString()
                val signal = sd.getSignalForDate(todayStr)
                if (signal != null) {
                    sleepHours = signal.sleepMinutes?.let { it / 60f }
                    waterMl = signal.waterMl ?: 0
                    kcalToday = signal.kcal ?: 0
                }
            }
        }

        val relevantMemories = mutableListOf<String>()
        runCatching {
            if (graphManager != null && embedder != null) {
                graphManager.initialize()
                val activeNodes = graphManager.getAllActiveNodes()
                if (activeNodes.isNotEmpty()) {
                    val retriever = MemoryRetriever(embedder)
                    val ranked = retriever.scoreAndRank(activeNodes, userMessage, maxResults = 2)
                    relevantMemories.addAll(ranked.map { it.first.rawText })
                }
            }
            if (relevantMemories.isEmpty()) {
                val facts = memoryFactDao.getAllFacts()
                if (facts.isNotEmpty()) {
                    relevantMemories.addAll(facts.take(2).map { it.fact })
                }
            }
        }

        val livingPrompt = AiSystemPrompt.buildLivingPrompt(
            userName = "Boss",
            sleepHours = sleepHours,
            waterMl = waterMl,
            kcalToday = kcalToday,
            recentMemories = relevantMemories
        )

        return if (contextSummary.isNotBlank()) {
            "$livingPrompt\n\n[ADDITIONAL CONTEXT]\n$contextSummary"
        } else {
            livingPrompt
        }
    }

    suspend fun generateJournalFeedback(journalText: String): String {
        val cleanText = journalText.trim()
        require(cleanText.isNotEmpty()) { "Journal entry is empty" }

        val request = """
            Read this journal entry and respond naturally, like a good friend.
            Reply in 1–3 sentences. Show you understood it. Do not diagnose, invent details,
            force advice, use emojis, start with a greeting, or mention being an AI.
            Return only the response that should appear under the journal entry.

            Journal entry:
            $cleanText
        """.trimIndent()

        val response = aiClient.sendMessage(
            messages = listOf(AiMessage(role = "user", content = request)),
            context = AiContext(section = "journal")
        )

        if (response.toolCalls.isNotEmpty()) {
            throw IllegalStateException("Journal feedback unexpectedly requested an app tool")
        }

        return response.message.trim().ifEmpty {
            throw IllegalStateException("AI returned empty journal feedback")
        }
    }

    fun restoreConversation(messages: List<AiMessage>) {
        conversation.clear()
        conversation.addAll(messages)
    }

    fun clearConversation() {
        conversation.clear()
    }

    private suspend fun runTool(toolCall: AiToolCall): String {
        val tool = toolRegistry.getTool(toolCall.name)
            ?: return "FAILED: Unknown tool '${toolCall.name}'. Nothing was executed."

        val missingRequired = tool.parameters
            .filter { it.required }
            .filter { toolCall.arguments[it.name].isNullOrBlank() }

        if (missingRequired.isNotEmpty()) {
            return "FAILED: Missing required parameter(s): " +
                    missingRequired.joinToString { it.name } +
                    ". Nothing was executed."
        }

        return try {
            actionRecorder.execute(toolCall.name, toolCall.arguments) {
                tool.execute(toolCall.arguments)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "FAILED: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun toolResultsFallback(): String {
        val results = conversation
            .takeLastWhile { it.role == "tool" }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }

        return if (results.isEmpty()) {
            "FRIDAY reached its action limit. Check the relevant screen for any completed actions."
        } else {
            results.joinToString("\n")
        }
    }
}
