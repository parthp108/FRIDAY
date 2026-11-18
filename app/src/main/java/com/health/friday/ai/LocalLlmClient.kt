package com.health.friday.ai

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Debug
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.health.friday.BuildConfig
import com.health.friday.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

/**
 * On-device LLM client (Qwen first, Gemma/Llama fallback) running through MediaPipe.
 *
 * What this class is responsible for:
 *  1. Building a prompt that FITS the model's context window (the tail - the user's
 *     message - is never cut off).
 *  2. Telling the model only about the tools that are relevant to this message.
 *  3. Parsing the model's output into a message or tool calls.
 *  4. Owning the model's RAM lifecycle: load on demand, refuse to load when the phone
 *     is short on memory, unload when idle or when Android asks for memory back.
 */
class LocalLlmClient(
    context: Context,
    private val toolRegistry: AiToolRegistry
) : AiClient {

    companion object {
        private const val TAG = "FRIDAY.LocalLlm"

        // MediaPipe's maxTokens is input + output combined. 1024 is the safe value for
        // the common Qwen .task files (their KV cache is usually 1280 or larger).
        private const val MAX_TOKENS = 1024
        private const val OUTPUT_RESERVE_TOKENS = 300
        // No tokenizer is available here, so this is a conservative heuristic.
        private const val CHARS_PER_TOKEN = 3.0
        private const val PROMPT_CHAR_BUDGET =
            ((MAX_TOKENS - OUTPUT_RESERVE_TOKENS) * CHARS_PER_TOKEN).toInt()

        private const val MAX_USER_CHARS = 500
        private const val MAX_TOOL_RESULT_CHARS = 500
        private const val MAX_HISTORY_TURNS = 4
        private const val MAX_HISTORY_CHARS = 220
        private const val MAX_TOOLS_IN_PROMPT = 6
        private const val MAX_CALLS_PER_REPLY = 3

        private const val IDLE_UNLOAD_MS = 4 * 60 * 1000L
        private const val HIDDEN_UNLOAD_MS = 60 * 1000L

        private const val TEMP_TOOLS = 0.2f
        private const val TEMP_CHAT = 0.6f
        private const val TOP_K = 20

        @Volatile
        private var cachedModelFile: File? = null

        @Volatile
        private var live: LocalLlmClient? = null

        fun getModelFile(context: Context): File? {
            cachedModelFile?.let { if (it.exists() && it.canRead()) return it }

            val dirs = listOfNotNull(
                context.getExternalFilesDir(null),
                context.filesDir,
                File("/sdcard/Download"),
                File("/sdcard/Downloads"),
                File("/sdcard")
            ).filter { it.exists() && it.isDirectory }

            val validExtensions = listOf(".bin", ".task")

            fun isModel(file: File) = file.isFile && file.canRead() &&
                    validExtensions.any { file.name.endsWith(it, ignoreCase = true) }

            // 1. A Qwen model file wins.
            for (dir in dirs) {
                val match = dir.listFiles()?.firstOrNull {
                    isModel(it) && it.name.contains("qwen", ignoreCase = true)
                }
                if (match != null) {
                    cachedModelFile = match
                    return match
                }
            }

            // 2. Otherwise any MediaPipe LLM file (Gemma, Llama...).
            for (dir in dirs) {
                val match = dir.listFiles()?.firstOrNull { isModel(it) }
                if (match != null) {
                    cachedModelFile = match
                    return match
                }
            }
            return null
        }

        /** Called from FridayApplication when Android reports memory pressure. */
        fun onTrimMemory(level: Int) {
            live?.handleTrimMemory(level)
        }

        fun onLowMemory() {
            live?.unloadNow("system low memory")
        }
    }

    private val appContext: Context = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "friday-local-llm-inference").apply {
            priority = Thread.NORM_PRIORITY - 2
        }
    }.asCoroutineDispatcher()

    // Lock order is always: generationMutex -> initializationMutex.
    private val initializationMutex = Mutex()
    private val generationMutex = Mutex()

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var template: Template = Template.QWEN

    @Volatile
    private var noThink: Boolean = false

    @Volatile
    private var initializationError: String? = null

    private var idleJob: Job? = null

    init {
        live = this
    }

    private enum class Template { QWEN, GEMMA }

    private class Turn(val role: String, val text: String)

    // ---------------------------------------------------------------------------------
    // RAM lifecycle
    // ---------------------------------------------------------------------------------

    /**
     * Warm the model in the background so the first chat is fast. Only does anything if the
     * local model is enabled in Settings AND the phone has comfortable free RAM.
     */
    fun preloadModel() {
        if (!SettingsRepository(appContext).isLocalLlmEnabled()) {
            Log.i(TAG, "Preload skipped: local LLM is disabled in Settings.")
            return
        }
        scope.launch {
            val loaded = getLlm(strictMemoryCheck = true)
            if (loaded != null) scheduleUnload(IDLE_UNLOAD_MS)
        }
    }

    /** Unload the model now (used by Settings or tests). */
    fun release() {
        unloadNow("release() called")
    }

    private fun handleTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ->
                unloadNow("trim level $level")
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                if (llmInference != null) scheduleUnload(HIDDEN_UNLOAD_MS)
        }
    }

    private fun scheduleUnload(afterMs: Long) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(afterMs)
            unloadIfIdle("idle for ${afterMs / 1000}s")
        }
    }

    private fun unloadNow(reason: String) {
        scope.launch { unloadIfIdle(reason) }
    }

    /** Closes the model unless a generation is running right now. */
    private suspend fun unloadIfIdle(reason: String) {
        if (!generationMutex.tryLock()) return
        try {
            initializationMutex.withLock {
                val current = llmInference ?: return
                withContext(singleThreadDispatcher) { runCatching { current.close() } }
                llmInference = null
                Log.i(TAG, "Model unloaded ($reason). RAM returned to the system.")
            }
        } finally {
            generationMutex.unlock()
        }
    }

    private fun hasRamFor(modelFile: File, strict: Boolean): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        // Weights + KV cache + runtime buffers are roughly 1.2-1.5x the file size.
        // These multipliers are estimates: tune them from the logs below.
        val multiplier = if (strict) 2.0 else 1.3
        val needed = (modelFile.length() * multiplier).toLong() + 300L * 1024 * 1024
        val ok = !info.lowMemory && info.availMem >= needed

        Log.i(
            TAG,
            "RAM check (${if (strict) "preload" else "on-demand"}): " +
                    "available=${info.availMem / MB} MB, needed=${needed / MB} MB, " +
                    "systemLow=${info.lowMemory} -> ${if (ok) "OK" else "REFUSED"}"
        )
        return ok
    }

    private suspend fun getLlm(strictMemoryCheck: Boolean = false): LlmInference? {
        llmInference?.let { return it }

        return initializationMutex.withLock {
            llmInference?.let { return@withLock it }

            withContext(singleThreadDispatcher) {
                val modelFile = getModelFile(appContext)
                if (modelFile == null) {
                    initializationError =
                        "No local model file found. Put a Qwen .task/.bin file in " +
                                "${appContext.getExternalFilesDir(null)?.absolutePath}/ or /sdcard/Download/"
                    Log.e(TAG, initializationError!!)
                    return@withContext null
                }

                if (!hasRamFor(modelFile, strictMemoryCheck)) {
                    initializationError = "Not enough free RAM to load ${modelFile.name} right now."
                    return@withContext null
                }

                val lowerName = modelFile.name.lowercase()
                template = if (lowerName.contains("qwen")) Template.QWEN else Template.GEMMA
                noThink = lowerName.contains("qwen3") || lowerName.contains("qwen-3")

                var lastError: Throwable? = null
                // CPU first for stability; GPU as fallback.
                for (backend in listOf(LlmInference.Backend.CPU, LlmInference.Backend.GPU)) {
                    try {
                        val started = System.currentTimeMillis()
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .setMaxTokens(MAX_TOKENS)
                            .setPreferredBackend(backend)
                            .build()

                        val instance = LlmInference.createFromOptions(appContext, options)
                        llmInference = instance
                        initializationError = null

                        val mem = Debug.MemoryInfo()
                        Debug.getMemoryInfo(mem)
                        Log.i(
                            TAG,
                            "Model loaded: ${modelFile.name} (${modelFile.length() / MB} MB file), " +
                                    "backend=$backend, ${System.currentTimeMillis() - started} ms, " +
                                    "template=$template, noThink=$noThink, " +
                                    "app PSS now=${mem.totalPss / 1024} MB"
                        )
                        return@withContext instance
                    } catch (e: Throwable) {
                        lastError = e
                        Log.w(TAG, "Load failed on $backend: ${e.message ?: e.javaClass.simpleName}")
                    }
                }

                initializationError = "Could not load ${modelFile.name}: " +
                        (lastError?.message ?: lastError?.javaClass?.simpleName ?: "unknown error")
                Log.e(TAG, initializationError!!, lastError)
                null
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // AiClient
    // ---------------------------------------------------------------------------------

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext,
        onPartialResponse: ((String) -> Unit)?
    ): AiResponse {
        idleJob?.cancel()

        val response = try {
            generationMutex.withLock { generate(messages, context) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Generation failed", e)
            AiResponse(message = "LOCAL_LLM_ERROR: ${e.message ?: e.javaClass.simpleName}")
        }

        scheduleUnload(IDLE_UNLOAD_MS)

        // The reveal happens OUTSIDE the generation lock so it never blocks the next request.
        if (onPartialResponse != null && response.toolCalls.isEmpty() && response.message.isNotBlank()) {
            revealText(response.message, onPartialResponse)
        }
        return response
    }

    private suspend fun generate(messages: List<AiMessage>, aiContext: AiContext): AiResponse {
        val llm = getLlm()
            ?: return AiResponse(
                message = "LOCAL_LLM_UNAVAILABLE: ${initializationError ?: "Local model could not be loaded."}"
            )

        val followUp = messages.lastOrNull()?.role == "tool"
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val tools = if (followUp) emptyList() else selectTools(userText)

        val prompt = buildPrompt(messages, aiContext, tools, followUp)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Prompt ${prompt.length}/$PROMPT_CHAR_BUDGET chars, tools=${tools.map { it.name }}")
        }

        val started = System.currentTimeMillis()
        val temperature = if (tools.isNotEmpty()) TEMP_TOOLS else TEMP_CHAT
        val raw = withContext(singleThreadDispatcher) {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(temperature)
                .build()
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            try {
                session.addQueryChunk(prompt)
                session.generateResponse().trim()
            } finally {
                runCatching { session.close() }
            }
        }
        Log.i(TAG, "Generated ${raw.length} chars in ${System.currentTimeMillis() - started} ms")
        if (BuildConfig.DEBUG) Log.d(TAG, "Raw output: $raw")

        return parseResponse(raw, followUp)
    }

    private suspend fun revealText(text: String, onPartial: (String) -> Unit) {
        val words = text.split(" ")
        val sb = StringBuilder()
        for (i in words.indices) {
            if (i > 0) sb.append(' ')
            sb.append(words[i])
            if (i % 3 == 2 || i == words.lastIndex) {
                val snapshot = sb.toString()
                withContext(Dispatchers.Main) { onPartial(snapshot) }
                delay(15)
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Tool selection: the model only sees tools relevant to this message
    // ---------------------------------------------------------------------------------

    private val toolGroups: List<Pair<Regex, List<String>>> = listOf(
        rx("ate|eat|eaten|eating|breakfast|lunch|dinner|snack|meal|calories?|kcal|protein|carbs?|fat|macros?|food") to
                listOf("log_meal", "log_estimated_meal", "get_today_nutrition", "get_week_nutrition", "correct_meal", "undo_last_action"),
        Regex("\\b(water|drank|drink|hydrat\\w*|litres?|liters?|glass(es)?|bottle)\\b|\\d\\s*(ml|l)\\b", RegexOption.IGNORE_CASE) to
                listOf("log_water", "get_today_nutrition", "undo_last_action"),
        rx("todo|to-do|task|tasks|goal|goals|remind\\w*|alarm|wake|schedule") to
                listOf("todo", "goal", "reminder", "set_alarm"),
        rx("sleep|slept|steps|workout|mood|screen\\s?time|my day|stats?|correlat\\w*|energy|productiv\\w*|how did i") to
                listOf("get_day", "query_life", "get_correlation", "predict_energy", "predict_productivity"),
        rx("remember|forget|journal|memory") to
                listOf("manage_memory", "search_journal"),
        rx("weather|temperature|news|release|latest|current|score|who is|when does") to
                listOf("web_search"),
        rx("focus|dnd|do not disturb") to
                listOf("start_focus_mode"),
        rx("play|spotify|song|music") to
                listOf("play_spotify_music")
    )

    private fun rx(words: String) = Regex("\\b($words)\\b", RegexOption.IGNORE_CASE)

    private fun selectTools(userText: String): List<AiTool> {
        val names = LinkedHashSet<String>()
        for ((pattern, group) in toolGroups) {
            if (pattern.containsMatchIn(userText)) names.addAll(group)
        }
        return names.mapNotNull { toolRegistry.getTool(it) }.take(MAX_TOOLS_IN_PROMPT)
    }

    private fun describeTool(tool: AiTool): String {
        val params = tool.parameters.joinToString(", ") { p ->
            val label = if (p.required) p.name else "${p.name}?"
            if (p.name == "action") "$label [${p.description.replace(Regex("\\s+"), " ").take(60)}]" else label
        }
        val desc = tool.description.replace(Regex("\\s+"), " ").trim()
            .substringBefore(". ").take(90)
        return "- ${tool.name}($params): $desc"
    }

    // ---------------------------------------------------------------------------------
    // Prompt building
    // ---------------------------------------------------------------------------------

    private fun buildPrompt(
        messages: List<AiMessage>,
        aiContext: AiContext,
        tools: List<AiTool>,
        followUp: Boolean
    ): String {
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        val tailMessages = if (lastUserIdx >= 0) messages.subList(lastUserIdx, messages.size) else messages.takeLast(1)
        val historyMessages = if (lastUserIdx > 0) messages.subList(0, lastUserIdx) else emptyList()

        val tail = tailMessages.map { toTurn(it) }
        val history = historyMessages
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(MAX_HISTORY_TURNS)
            .map { Turn(it.role, it.content.take(MAX_HISTORY_CHARS)) }

        // From most to least generous. The first attempt that fits the budget wins,
        // and the tail (the user's message) is never dropped.
        val attempts = listOf(
            Triple(300, 900, history.size),
            Triple(300, 900, 2),
            Triple(150, 600, 0),
            Triple(0, 350, 0),
            Triple(0, 0, 0)
        )

        var best = ""
        for ((bioCap, toolCap, historyCount) in attempts) {
            val system = buildSystem(aiContext, tools, followUp, bioCap, toolCap)
            val turns = history.takeLast(historyCount) + tail
            best = render(system, turns)
            if (best.length <= PROMPT_CHAR_BUDGET) return best
        }
        Log.w(TAG, "Prompt still over budget (${best.length}/$PROMPT_CHAR_BUDGET) after trimming.")
        return best
    }

    private fun buildSystem(
        aiContext: AiContext,
        tools: List<AiTool>,
        followUp: Boolean,
        bioCap: Int,
        toolCap: Int
    ): String = buildString {
        append("You are FRIDAY, the user's personal assistant inside their phone.\n")
        append(FridayPersona.timeLine()).append('\n')
        append(FridayPersona.compactRules()).append('\n')

        val bio = extractBio(aiContext.summary, bioCap)
        if (bio.isNotBlank()) append("Context (do not recite unless asked): ").append(bio).append('\n')

        if (followUp) {
            append("A tool just ran. Use ONLY the tool result below. In 1-2 sentences say what was done or what failed. ")
            append("If the result says FAILED or SKIPPED, say it did not happen. Never invent numbers.")
            return@buildString
        }

        val toolLines = StringBuilder()
        for (tool in tools) {
            val line = describeTool(tool) + "\n"
            if (toolLines.length + line.length > toolCap) break
            toolLines.append(line)
        }

        if (toolLines.isNotEmpty()) {
            append("\nTOOLS:\n").append(toolLines)
            append("(? = optional)\n")
            append("To use a tool, reply with ONLY this JSON and nothing else: ")
            append("{\"name\":\"TOOL\",\"arguments\":{\"param\":\"value\"}}\n")
            append("Several actions: {\"tool_calls\":[{...},{...}]}\n")
            append("Pass the user's own words as the text argument. Never invent numbers. ")
            append("If no tool fits, answer in plain text.")
        } else {
            append("Answer in plain text. Be brief.")
        }
    }

    /**
     * Pulls only the useful facts out of the orchestrator's context block. The orchestrator
     * currently embeds a whole persona in there; we want just the bullet facts.
     */
    private fun extractBio(summary: String, cap: Int): String {
        if (summary.isBlank() || cap <= 0) return ""
        val facts = summary.substringBefore("[ADDITIONAL CONTEXT]")
            .lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") && !it.endsWith(":") && !it.contains("Unknown") }
            .map { it.removePrefix("- ") }
        val additional = summary.substringAfter("[ADDITIONAL CONTEXT]", "")
            .replace(Regex("\\s+"), " ").trim()
        return (facts + listOfNotNull(additional.ifBlank { null })).joinToString("; ").take(cap)
    }

    private fun toTurn(message: AiMessage): Turn = when (message.role) {
        "tool" -> Turn("user", "<tool_response>\n${cleanToolMessage(message.content)}\n</tool_response>")
        "assistant" -> Turn("assistant", message.content.take(MAX_USER_CHARS))
        else -> Turn("user", message.content.take(MAX_USER_CHARS))
    }

    private fun cleanToolMessage(content: String): String {
        var text = content.replace(" Now reply to the user naturally in character.", "").trim()
        // The orchestrator labels every result "succeeded", even FAILED/SKIPPED ones.
        text = text.replace(Regex("^Tool '([^']+)' succeeded: (FAILED|SKIPPED)"), "Tool '$1' did NOT run: $2")
        return text.take(MAX_TOOL_RESULT_CHARS)
    }

    private fun render(system: String, rawTurns: List<Turn>): String {
        // Merge consecutive turns from the same role (Gemma requires alternation).
        val turns = mutableListOf<Turn>()
        for (t in rawTurns) {
            val last = turns.lastOrNull()
            if (last != null && last.role == t.role) {
                turns[turns.lastIndex] = Turn(t.role, last.text + "\n" + t.text)
            } else {
                turns.add(t)
            }
        }

        return if (template == Template.QWEN) {
            buildString {
                append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                for (t in turns) {
                    append("<|im_start|>").append(t.role).append('\n')
                        .append(t.text).append("<|im_end|>\n")
                }
                append("<|im_start|>assistant\n")
                if (noThink) append("<think>\n\n</think>\n\n")
            }
        } else {
            buildString {
                var systemPending = true
                for (t in turns) {
                    val role = if (t.role == "assistant") "model" else "user"
                    append("<start_of_turn>").append(role).append('\n')
                    if (systemPending && role == "user") {
                        append(system).append("\n\n")
                        systemPending = false
                    }
                    append(t.text).append("<end_of_turn>\n")
                }
                append("<start_of_turn>model\n")
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Output parsing
    // ---------------------------------------------------------------------------------

    private fun parseResponse(rawOutput: String, followUp: Boolean): AiResponse {
        var cleaned = rawOutput
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("<|im_start|>assistant", "")
            .replace("<|im_end|>", "")
            .replace("<start_of_turn>model", "")
            .replace("<end_of_turn>", "")
            .trim()

        // Qwen3 reasoning block: drop it. If it was cut off by the token limit, drop the rest too.
        if (cleaned.contains("<think>")) {
            cleaned = if (cleaned.contains("</think>")) {
                cleaned.substringAfter("</think>")
            } else {
                cleaned.substringBefore("<think>")
            }.trim()
        }

        if (cleaned.isEmpty()) return AiResponse(message = "LOCAL_LLM_ERROR: empty output")

        if (cleaned.startsWith("{")) {
            val json = parseJsonLoosely(cleaned)
                ?: return AiResponse(message = "LOCAL_LLM_ERROR: could not parse model JSON")

            val calls = extractToolCalls(json)
            if (calls.isNotEmpty()) {
                return AiResponse(message = "", toolCalls = calls.take(MAX_CALLS_PER_REPLY))
            }
            if (json.has("name") || json.has("tool_calls")) {
                // The model tried a tool that does not exist. Say so honestly.
                return AiResponse(message = "I don't have a tool for that yet.")
            }
            val message = json.optString("message").trim()
            if (message.isNotEmpty()) return AiResponse(message = message)
            return AiResponse(message = "LOCAL_LLM_ERROR: unexpected JSON shape")
        }

        if (followUp && cleaned.contains("tool_calls")) {
            return AiResponse(message = "LOCAL_LLM_ERROR: tool call in follow-up")
        }

        return AiResponse(message = cleaned.take(2500))
    }

    private fun extractToolCalls(root: JSONObject): List<AiToolCall> {
        val items = mutableListOf<JSONObject>()
        val array: JSONArray? = root.optJSONArray("tool_calls")
        if (array != null) {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { items.add(it) }
        } else if (root.has("name")) {
            items.add(root)
        }

        val seen = HashSet<String>()
        val calls = mutableListOf<AiToolCall>()
        for (item in items) {
            val name = item.optString("name").trim()
            if (name.isEmpty() || toolRegistry.getTool(name) == null) continue

            val argsObject = item.optJSONObject("arguments") ?: item.optJSONObject("args") ?: JSONObject()
            val args = mutableMapOf<String, String>()
            val keys = argsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = argsObject.opt(key)
                if (value != null && value != JSONObject.NULL) args[key] = value.toString()
            }

            val signature = name + "|" + args.toSortedMap()
            if (seen.add(signature)) calls.add(AiToolCall(name = name, arguments = args))
        }
        return calls
    }

    /** Small models often forget a closing brace. Try the text as-is, then with braces added. */
    private fun parseJsonLoosely(text: String): JSONObject? {
        val start = text.indexOf('{')
        if (start < 0) return null
        val body = text.substring(start)
        val end = body.lastIndexOf('}')

        val candidates = mutableListOf<String>()
        if (end > 0) candidates.add(body.substring(0, end + 1))

        val opens = body.count { it == '{' }
        val closes = body.count { it == '}' }
        if (opens > closes && opens - closes <= 3) {
            candidates.add(body + "}".repeat(opens - closes))
        }

        for (candidate in candidates) {
            try {
                return JSONObject(candidate)
            } catch (_: Exception) {
                // try next candidate
            }
        }
        return null
    }

    private val MB = 1024L * 1024L
}
