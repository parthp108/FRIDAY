package com.health.friday.ai

import com.health.friday.settings.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException

// Uses Gemini when a key is set, and the offline client otherwise
// or when Gemini fails.
class RoutingAiClient(
    private val settings: SettingsRepository,
    private val gemini: AiClient,
    private val local: AiClient
) : AiClient {

    private var activeClient: AiClient = local

    private var pendingNotice: String = ""

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse {

        val secondPass =
            messages.lastOrNull()?.role == "tool"

        if (secondPass) {

            if (activeClient === gemini) {

                try {
                    return gemini.sendMessage(messages, context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pendingNotice =
                        "Gemini failed (${e.message ?: "unknown error"}). " +
                                "Showing the raw result.\n"
                }
            }

            val relay = local.sendMessage(messages, context)

            val notice = pendingNotice
            pendingNotice = ""

            return relay.copy(message = notice + relay.message)
        }

        if (settings.getApiKey().isNotEmpty()) {

            try {

                val response = gemini.sendMessage(messages, context)

                activeClient = gemini
                pendingNotice = ""

                return response

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingNotice =
                    "Gemini unavailable (${e.message ?: "unknown error"}). " +
                            "Using offline mode.\n"
            }
        }

        activeClient = local

        val response = local.sendMessage(messages, context)

        if (response.toolCalls.isEmpty()) {

            val notice = pendingNotice
            pendingNotice = ""

            return response.copy(message = notice + response.message)
        }

        // Tool calls follow, so the notice is shown with the result.
        return response
    }
}