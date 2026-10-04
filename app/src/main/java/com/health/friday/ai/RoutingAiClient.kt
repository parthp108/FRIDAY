
package com.health.friday.ai

import com.health.friday.settings.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException

// Uses Gemini when a key is set, and the offline client otherwise.
// Explicit local alarm commands are handled by the local tool parser
// so Gemini cannot turn them into a plain-text response.
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

            val relay =
                local.sendMessage(
                    messages,
                    context
                )

            val notice = pendingNotice
            pendingNotice = ""

            return relay.copy(
                message = notice + relay.message
            )
        }

        /*
         * Alarms are handled locally.
         *
         * This guarantees that a request such as
         * "set an alarm for 7:30 AM"
         * reaches AlarmTool even when Gemini is configured but
         * does not choose the alarm tool.
         */
        val latestUserMessage =
            messages
                .lastOrNull { it.role == "user" }
                ?.content
                ?.lowercase()
                ?.trim()
                ?: ""

        val looksLikeAlarm =
            Regex(
                "\\b(alarm|alarms|wake me|wake-up|wake up)\\b"
            ).containsMatchIn(latestUserMessage)

        if (looksLikeAlarm) {

            activeClient = local

            return local.sendMessage(
                messages,
                context
            )
        }

        if (settings.getApiKey().isNotEmpty()) {

            try {

                val response =
                    gemini.sendMessage(
                        messages,
                        context
                    )

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

        val response =
            local.sendMessage(
                messages,
                context
            )

        if (response.toolCalls.isEmpty()) {

            val notice = pendingNotice
            pendingNotice = ""

            return response.copy(
                message = notice + response.message
            )
        }

        return response
    }
}

