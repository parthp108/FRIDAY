package com.health.friday.ai

import com.health.friday.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiAiClient(
    private val settings: SettingsRepository,
    private val toolRegistry: AiToolRegistry
) : AiClient {

    private data class PendingCall(
        val name: String,
        val id: String?
    )

    // The model's latest tool-calling turn, kept exactly as received.
    private var pendingModelContent: JSONObject? = null
    private var pendingCalls: List<PendingCall> = emptyList()

    // Every tool exchange (model call + our results) of the current user turn,
    // in order, so later rounds still see the earlier ones.
    private val turnExchanges = mutableListOf<JSONObject>()
    private var turnToolCount = 0

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse = withContext(Dispatchers.IO) {

        val apiKey = settings.getApiKey()

        if (apiKey.isEmpty()) {
            throw IOException("No Gemini API key set")
        }

        val model = settings.getModel()

        if (!Regex("^[A-Za-z0-9._-]+$").matches(model)) {
            throw IOException("Invalid model name \"$model\"")
        }

        val toolTail =
            if (messages.lastOrNull()?.role == "tool") {
                messages.takeLastWhile { it.role == "tool" }
            } else {
                emptyList()
            }

        if (toolTail.isEmpty()) {
            // A fresh user message starts a fresh turn.
            turnExchanges.clear()
            turnToolCount = 0
            pendingModelContent = null
            pendingCalls = emptyList()
        }

        val historyEnd = messages.size - toolTail.size

        // Plain text history. Consecutive same-role messages are merged.
        val turns = mutableListOf<Pair<String, String>>()

        for (message in messages.subList(0, historyEnd)) {

            val role =
                when (message.role) {
                    "user" -> "user"
                    "assistant" -> "model"
                    else -> continue
                }

            if (message.content.isBlank()) {
                continue
            }

            val last = turns.lastOrNull()

            if (last != null && last.first == role) {
                turns[turns.size - 1] =
                    Pair(role, last.second + "\n" + message.content)
            } else {
                turns.add(Pair(role, message.content))
            }
        }

        if (toolTail.isNotEmpty()) {

            val modelContent =
                pendingModelContent
                    ?: throw IOException("Lost the model's tool call")

            val newResults = toolTail.drop(turnToolCount)

            val parts = JSONArray()

            newResults.forEachIndexed { index, toolMessage ->

                val call = pendingCalls.getOrNull(index)

                if (call != null) {

                    val functionResponse =
                        JSONObject()
                            .put("name", call.name)
                            .put(
                                "response",
                                JSONObject().put("result", toolMessage.content)
                            )

                    if (call.id != null) {
                        functionResponse.put("id", call.id)
                    }

                    parts.put(
                        JSONObject().put("functionResponse", functionResponse)
                    )
                }
            }

            turnExchanges.add(modelContent)

            turnExchanges.add(
                JSONObject()
                    .put("role", "user")
                    .put("parts", parts)
            )

            turnToolCount = toolTail.size
            pendingModelContent = null
        }

        val contents = JSONArray()

        for (turn in turns) {
            contents.put(textContent(turn.first, turn.second))
        }

        for (exchange in turnExchanges) {
            contents.put(exchange)
        }

        val body =
            JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", systemPrompt())
                        )
                    )
                )
                .put("contents", contents)
                .put("tools", toolDeclarations())
                .put(
                    "generationConfig",
                    JSONObject().put("temperature", 0.2)
                )

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val response =
            JSONObject(post(url, apiKey, body.toString()))

        parseResponse(response)
    }

    private fun parseResponse(
        root: JSONObject
    ): AiResponse {

        val candidate =
            root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("Gemini returned no answer")

        val content = candidate.optJSONObject("content")

        val parts = content?.optJSONArray("parts")

        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        val pending = mutableListOf<PendingCall>()

        if (parts != null) {

            for (i in 0 until parts.length()) {

                val part = parts.optJSONObject(i) ?: continue

                val functionCall = part.optJSONObject("functionCall")

                if (functionCall != null) {

                    val name = functionCall.optString("name")

                    val id =
                        functionCall.optString("id", "").ifEmpty { null }

                    val args = mutableMapOf<String, String>()

                    val argsJson = functionCall.optJSONObject("args")

                    if (argsJson != null) {

                        val keys = argsJson.keys()

                        while (keys.hasNext()) {

                            val key = keys.next()
                            val value = argsJson.opt(key)

                            if (value != null && value != JSONObject.NULL) {
                                args[key] = value.toString()
                            }
                        }
                    }

                    calls.add(AiToolCall(name = name, arguments = args))
                    pending.add(PendingCall(name = name, id = id))

                } else if (
                    part.has("text") &&
                    !part.optBoolean("thought", false)
                ) {
                    text.append(part.optString("text"))
                }
            }
        }

        if (calls.isNotEmpty()) {

            pendingModelContent = content
            pendingCalls = pending

            return AiResponse(
                message = text.toString().trim(),
                toolCalls = calls
            )
        }

        pendingModelContent = null
        pendingCalls = emptyList()
        turnExchanges.clear()
        turnToolCount = 0

        val reply = text.toString().trim()

        if (reply.isEmpty()) {
            throw IOException(
                "Gemini returned no text (finish reason: " +
                        "${candidate.optString("finishReason", "unknown")})"
            )
        }

        return AiResponse(message = reply)
    }

    private fun textContent(
        role: String,
        text: String
    ): JSONObject {

        return JSONObject()
            .put("role", role)
            .put(
                "parts",
                JSONArray().put(JSONObject().put("text", text))
            )
    }

    private fun toolDeclarations(): JSONArray {

        val declarations = JSONArray()

        for (tool in toolRegistry.getTools()) {

            val declaration =
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)

            if (tool.parameters.isNotEmpty()) {

                val properties = JSONObject()
                val required = JSONArray()

                for (parameter in tool.parameters) {

                    properties.put(
                        parameter.name,
                        JSONObject()
                            .put("type", "string")
                            .put("description", parameter.description)
                    )

                    if (parameter.required) {
                        required.put(parameter.name)
                    }
                }

                val schema =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", properties)

                if (required.length() > 0) {
                    schema.put("required", required)
                }

                declaration.put("parameters", schema)
            }

            declarations.put(declaration)
        }

        return JSONArray().put(
            JSONObject().put("functionDeclarations", declarations)
        )
    }

    private fun systemPrompt(): String {

        val today =
            SimpleDateFormat("EEEE yyyy-MM-dd", Locale.US).format(Date())

        return """
            You are FRIDAY, the personal assistant inside the user's own Android app. Today is $today.
            Be brief and direct. No greetings, no filler, no emojis.

            General rules:
            - To record food or water, always call the tools. Never say something was logged unless a tool result says so.
            - Questions about today: call get_today_nutrition. Questions about the week or trends: call get_week_nutrition.
            - If the user asks for something your tools cannot do, say so.

            Logging food:
            - log_meal only recognises a few exact foods listed in log_estimated_meal's description. Use it for those, with the user's words in text. Use its date only when the day is not today (yesterday, N days ago, or YYYY-MM-DD worked out from today's date). Set meal_type only if the user said or clearly implied it.
            - For every other food or dish, call log_estimated_meal right away, once per dish. Do not first try log_meal, and do not ask the user for numbers.
            - A sentence can contain both kinds. Split it: known foods to log_meal, the rest to log_estimated_meal.
            - Estimate like a nutritionist for the portion the user actually ate. If the portion is not stated, assume a typical single serving and say which. Multiply for quantities (2 bowls = double). Restaurant and home-style dishes usually contain more oil than people expect. Give one best-guess number per field, and make calories consistent with the macros (4 kcal per g protein and carbs, 9 per g fat).
            - Put the dish and portion in the name, like "Chicken biryani (1 bowl, about 350 g)".
            - After an estimate, tell the user it is an estimate, the portion you assumed, and the rough range. Tell them they can delete it with the cross on the Nutrition tab if it is wrong.
            - You cannot look things up on the internet. If asked, say estimates come from your general nutrition knowledge.

            Logging water:
            - log_water takes millilitres. If the user says glasses or bottles without a size, assume glass = 250 ml and bottle = 500 ml, and tell them the assumption.

            Summaries:
            - Talk like a blunt friend. Call out overeating, low protein or missed logging plainly, but only when the numbers support it. Days with no meals logged are unknown, not zero. Do not shame the user and do not make medical claims.
        """.trimIndent()
    }

    private fun post(
        url: String,
        apiKey: String,
        body: String
    ): String {

        val connection = URL(url).openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)

            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""

            if (code !in 200..299) {
                throw IOException(errorMessage(code, text))
            }

            return text

        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(
        code: Int,
        body: String
    ): String {

        val detail =
            try {
                JSONObject(body)
                    .optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
            } catch (e: Exception) {
                ""
            }

        val hint =
            when (code) {
                400, 401, 403 -> "check the API key and model name"
                404 -> "model not found, check the model name"
                429 -> "rate limit or quota reached"
                else -> "server problem"
            }

        return "HTTP $code ($hint)" +
                if (detail.isNotEmpty()) ": ${detail.take(160)}" else ""
    }
}