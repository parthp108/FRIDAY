package com.health.friday.shared.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val system: String,
    val stream: Boolean = false
)

@Serializable
private data class OllamaGenerateResponse(val response: String = "")

class LocalOllamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun generateStreamingChatResponse(
        systemPrompt: String,
        userPrompt: String,
        preferredModel: String = "llama3.2:1b",
        onToken: (String) -> Unit
    ): Boolean {
        val modelsToTry = listOf(preferredModel, "llama3.2:1b", "llama3.2").distinct()

        for (model in modelsToTry) {
            val success = queryOllamaStreaming(baseUrl, model, systemPrompt, userPrompt, onToken)
                || queryOllamaStreaming("http://localhost:11434", model, systemPrompt, userPrompt, onToken)

            if (success) {
                return true
            }
        }
        return false
    }

    suspend fun generateChatResponse(
        systemPrompt: String,
        userPrompt: String,
        preferredModel: String = "llama3.2:1b"
    ): String? {
        val sb = StringBuilder()
        val success = generateStreamingChatResponse(systemPrompt, userPrompt, preferredModel) { token ->
            sb.append(token)
        }
        return if (success && sb.isNotBlank()) sb.toString() else null
    }

    private fun queryOllamaStreaming(
        hostUrl: String,
        modelName: String,
        systemPrompt: String,
        userPrompt: String,
        onToken: (String) -> Unit
    ): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$hostUrl/api/generate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 600000

            val reqObj = OllamaGenerateRequest(
                model = modelName,
                prompt = userPrompt,
                system = systemPrompt,
                stream = true
            )
            val jsonBody = json.encodeToString(OllamaGenerateRequest.serializer(), reqObj)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                var hasTokens = false
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        runCatching {
                            val chunk = json.decodeFromString(OllamaGenerateResponse.serializer(), line)
                            if (chunk.response.isNotEmpty()) {
                                hasTokens = true
                                onToken(chunk.response)
                            }
                        }
                    }
                    line = reader.readLine()
                }
                hasTokens
            } else {
                System.err.println("[OllamaClient] HTTP ${conn.responseCode} from $hostUrl")
                false
            }
        } catch (e: Exception) {
            System.err.println("[OllamaClient] Exception querying $hostUrl ($modelName): ${e.javaClass.simpleName} - ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
