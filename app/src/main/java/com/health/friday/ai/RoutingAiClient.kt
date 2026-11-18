package com.health.friday.ai

import android.util.Log
import com.health.friday.device.ThermalGuard
import com.health.friday.settings.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException

class RoutingAiClient(
    private val settings: SettingsRepository,
    private val localLlmClient: AiClient?,
    private val cloudClient: AiClient,
    private val thermalGuard: ThermalGuard? = null
) : AiClient {

    @Volatile
    private var activeClient: AiClient? = null

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext,
        onPartialResponse: ((String) -> Unit)?
    ): AiResponse {
        val isToolFollowUp = messages.lastOrNull()?.role == "tool"
        if (isToolFollowUp) {
            return sendFollowUp(messages, context, onPartialResponse)
        }

        val lastUserText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val hasCloudKey = settings.getApiKey().isNotBlank()

        // 1. If the user is asking a vast world-knowledge, academic, or live-internet question
        //    AND Cloud Gemini is available, route to Cloud first for deep, intelligent answers.
        if (hasCloudKey && !isPersonalAppCommand(lastUserText)) {
            try {
                Log.i(TAG, "🔀 [ROUTER] Route 1: Sending world-knowledge query to Cloud Gemini LLM...")
                val cloud = cloudClient.sendMessage(messages, context, onPartialResponse)
                if (isUsable(cloud)) {
                    Log.i(TAG, "✅ [ROUTER MATCH] Handled by Cloud Gemini LLM")
                    activeClient = cloudClient
                    return cloud
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Cloud Gemini failed: ${describe(e)}. Falling back to local LLM.", e)
            }
        }

        // 2. Local Gemma / Qwen (handles personal app commands, offline chat, and offline web_search calls)
        val canRunLocalLlm = localLlmClient != null &&
                settings.isLocalLlmEnabled() &&
                thermalGuard?.isDeviceOverheating() != true

        if (canRunLocalLlm && localLlmClient != null) {
            try {
                Log.i(TAG, "🔀 [ROUTER] Route 2: Sending request to On-device Local LLM...")
                val local = localLlmClient.sendMessage(messages, context, onPartialResponse)
                val localFailedOrRefused = isLocalRefusalOrError(local.message)

                if (!localFailedOrRefused && isUsable(local)) {
                    Log.i(TAG, "✅ [ROUTER MATCH] Handled by On-device Local LLM")
                    activeClient = localLlmClient
                    return local
                }

                if (localFailedOrRefused) {
                    Log.w(TAG, "⚠️ Local LLM refused or failed: ${local.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Local LLM threw error: ${describe(e)}", e)
            }
        }

        // 3. Cloud fallback if Local LLM refused, failed, or didn't know the answer
        if (hasCloudKey) {
            try {
                Log.i(TAG, "🔀 [ROUTER] Route 3: Escalating refused/failed request to Cloud Gemini...")
                val cloud = cloudClient.sendMessage(messages, context, onPartialResponse)
                if (isUsable(cloud)) {
                    Log.i(TAG, "✅ [ROUTER MATCH] Handled by Cloud Gemini LLM (Escalated)")
                    activeClient = cloudClient
                    return cloud
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Cloud Gemini escalation failed: ${describe(e)}", e)
            }
        }

        return AiResponse(message = "I couldn't process that request right now. Please check your AI model or connection settings.")
    }

    /**
     * Checks if the message is specifically about local app actions (meals, water, alarms, tasks, stats).
     * Everything else (fluid dynamics, MCU lore, live weather, movie releases) is World Knowledge.
     */
    private fun isPersonalAppCommand(text: String): Boolean {
        val lower = text.lowercase()
        val appKeywords = listOf(
            "log ", "logged", "water", "ml ", "kcal", "calorie", "protein", "carb", "fat",
            "meal", "ate ", "breakfast", "lunch", "dinner", "alarm", "wake me", "remind",
            "todo", "task", "snapshot", "my day", "my sleep", "my steps", "correlat",
            "journal", "energy forecast", "focus mode", "dnd"
        )
        return appKeywords.any { lower.contains(it) }
    }

    /**
     * Catches Gemma's "I don't have real-time info" or JSON parse failures so we can escalate to Cloud.
     */
    private fun isLocalRefusalOrError(msg: String): Boolean {
        val lower = msg.lowercase()
        return msg.startsWith("LOCAL_LLM_UNAVAILABLE:") ||
                msg.startsWith("LOCAL_LLM_ERROR:") ||
                lower.contains("couldn't interpret gemma's response") ||
                lower.contains("don't have real-time") ||
                lower.contains("do not have real-time") ||
                lower.contains("don't have access to real-time") ||
                lower.contains("cannot browse the internet") ||
                lower.contains("don't have enough information")
    }

    private suspend fun sendFollowUp(
        messages: List<AiMessage>,
        context: AiContext,
        onPartialResponse: ((String) -> Unit)?
    ): AiResponse {
        val hasCloudKey = settings.getApiKey().isNotBlank()
        val canRunLocalLlm = localLlmClient != null &&
                settings.isLocalLlmEnabled() &&
                thermalGuard?.isDeviceOverheating() != true

        activeClient?.let { client ->
            try {
                val response = client.sendMessage(messages, context, onPartialResponse)
                if (isUsable(response)) return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Active client failed on follow-up: ${describe(e)}", e)
            }
        }

        if (hasCloudKey) {
            try {
                val cloudResponse = cloudClient.sendMessage(messages, context, onPartialResponse)
                if (isUsable(cloudResponse)) {
                    activeClient = cloudClient
                    return cloudResponse
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Cloud Gemini failed on follow-up: ${describe(e)}", e)
            }
        }

        if (canRunLocalLlm && localLlmClient != null) {
            try {
                val localResponse = localLlmClient.sendMessage(messages, context, onPartialResponse)
                if (isUsable(localResponse)) {
                    activeClient = localLlmClient
                    return localResponse
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ Local LLM failed on follow-up: ${describe(e)}", e)
            }
        }

        return fallbackToolResult(messages)
    }

    private fun fallbackToolResult(messages: List<AiMessage>): AiResponse {
        val results = messages
            .takeLastWhile { it.role == "tool" }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }

        return AiResponse(
            message = if (results.isEmpty()) {
                "The action finished, but FRIDAY couldn't prepare a response."
            } else {
                results.joinToString("\n")
            }
        )
    }

    private fun isUsable(response: AiResponse): Boolean {
        return response.toolCalls.isNotEmpty() || response.message.isNotBlank()
    }

    private fun describe(e: Throwable): String =
        clean(e.message ?: e.javaClass.simpleName)

    private fun clean(text: String): String =
        text.replace(Regex("AIza[0-9A-Za-z_\\-]{20,}"), "[key]")
            .replace('\n', ' ')
            .trim()
            .trimEnd('.')
            .take(140)

    private companion object {
        const val TAG = "FRIDAY.Route"
    }
}
