package com.health.friday.shared.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SharedGeminiPart(val text: String)

@Serializable
data class SharedGeminiContent(val role: String = "user", val parts: List<SharedGeminiPart>)

@Serializable
data class SharedGeminiRequest(val contents: List<SharedGeminiContent>)

@Serializable
data class SharedGeminiCandidate(val content: SharedGeminiContent? = null)

@Serializable
data class SharedGeminiResponse(val candidates: List<SharedGeminiCandidate> = emptyList())

class SharedGeminiClient(
    private val defaultModel: String = "gemini-3.5-flash-lite"
) {
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateContent(
        prompt: String,
        apiKey: String,
        systemPrompt: String = "",
        modelOverride: String? = null
    ): String? {
        if (apiKey.isBlank()) return null
        val model = modelOverride ?: defaultModel
        val combinedPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\nUser: $prompt" else prompt

        return try {
            val req = SharedGeminiRequest(
                contents = listOf(
                    SharedGeminiContent("user", listOf(SharedGeminiPart(combinedPrompt)))
                )
            )
            val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", apiKey)
                setBody(json.encodeToString(SharedGeminiRequest.serializer(), req))
            }

            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString(SharedGeminiResponse.serializer(), response.bodyAsText())
                    .candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
