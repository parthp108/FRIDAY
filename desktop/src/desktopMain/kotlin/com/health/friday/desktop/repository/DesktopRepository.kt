package com.health.friday.desktop.repository

import com.health.friday.desktop.ai.DesktopAiClient
import com.health.friday.desktop.bridge.DesktopIdentity
import com.health.friday.desktop.bridge.DesktopKtorServer
import com.health.friday.desktop.bridge.DesktopSyncClient
import com.health.friday.desktop.sync.cloud.DesktopCloudSyncRemoteApplier
import com.health.friday.desktop.sync.cloud.DesktopCloudSyncRuntime
import com.health.friday.desktop.sync.cloud.DesktopDriveSyncResult
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import com.health.friday.shared.sync.cloud.JournalSyncPayload
import com.health.friday.shared.sync.cloud.MealSyncPayload
import com.health.friday.shared.sync.cloud.TodoSyncPayload
import com.health.friday.shared.sync.cloud.WaterSyncPayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.SupervisorJob
import com.health.friday.shared.ai.LocalOllamaClient
import com.health.friday.shared.ai.SharedAiOrchestrator
import com.health.friday.shared.models.*
import com.health.friday.shared.protocol.*
import com.health.friday.shared.tools.WebSearchTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DesktopChatStorage {
    private val sessionsFile by lazy {
        val dir = File("D:/FRIDAY").apply { if (!exists()) mkdirs() }
        File(dir, "desktop_chat_sessions.json")
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun loadSessions(): List<ChatSessionDto> {
        return try {
            if (sessionsFile.exists()) {
                val text = sessionsFile.readText()
                if (text.isNotBlank()) {
                    json.decodeFromString<List<ChatSessionDto>>(text)
                } else emptyList()
            } else emptyList()
        } catch (e: Exception) {
            System.err.println("[ChatStorage] Load error: ${e.message}")
            emptyList()
        }
    }

    fun saveSessions(sessions: List<ChatSessionDto>) {
        try {
            val jsonText = json.encodeToString(sessions)
            sessionsFile.writeText(jsonText)
        } catch (e: Exception) {
            System.err.println("[ChatStorage] Save error: ${e.message}")
        }
    }

    fun clearAll() {
        try {
            if (sessionsFile.exists()) {
                sessionsFile.delete()
            }
        } catch (_: Exception) {}
    }
}

class DesktopRepository(
    val orchestrator: SharedAiOrchestrator,
    val syncClient: DesktopSyncClient = DesktopSyncClient(),
    private val ollamaClient: LocalOllamaClient = LocalOllamaClient(),
    private val aiClient: DesktopAiClient = DesktopAiClient(orchestrator, ollamaClient)
) {
    private companion object {
        const val DEFAULT_KCAL_GOAL = 2000
        const val DEFAULT_WATER_GOAL_ML = 2500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val webSearchTool = WebSearchTool()

    /** One-line banner shown in the UI (read-only rejections etc.). */
    val notice = MutableStateFlow<String?>(null)

    private fun readOnly(action: String) {
        notice.value = "$action isn't available yet: the phone link is read-only until secure pairing is built."
    }

    fun clearNotice() { notice.value = null }

    val nutritionRepo = NutritionRepository(this::readOnly)
    val healthRepo = HealthRepository(this::readOnly)
    val tasksRepo = TasksRepository(this::readOnly)
    val healthArchiveRepo = HealthArchiveRepository()

    private val cloudRemoteApplier = DesktopCloudSyncRemoteApplier(
        nutritionRepository = nutritionRepo,
        healthRepository = healthRepo,
        tasksRepository = tasksRepo,
        healthArchiveRepository = healthArchiveRepo
    )
    val cloudSyncRuntime = DesktopCloudSyncRuntime(
        deviceId = DesktopIdentity.deviceId,
        remoteApplier = cloudRemoteApplier
    )

    private val defaultWelcomeMsg = ChatMessageDto(
        "1", ChatRole.ASSISTANT,
        "F.R.I.D.A.Y. Desktop online. Connect your phone by USB (USB debugging on) to see its data.",
        System.currentTimeMillis()
    )

    val sessions = MutableStateFlow<List<ChatSessionDto>>(emptyList())
    val currentSessionId = MutableStateFlow<String>("")
    val messages = MutableStateFlow<List<ChatMessageDto>>(listOf(defaultWelcomeMsg))

    init {
        orchestrator.registerTool(webSearchTool)
        loadInitialSessions()
    }

    private fun loadInitialSessions() {
        val loaded = DesktopChatStorage.loadSessions()
        if (loaded.isNotEmpty()) {
            sessions.value = loaded
            val active = loaded.first()
            currentSessionId.value = active.id
            messages.value = active.messages.ifEmpty { listOf(defaultWelcomeMsg) }
        } else {
            createNewSession()
        }
    }

    fun createNewSession() {
        val freshId = "session_${System.currentTimeMillis()}"
        val freshSession = ChatSessionDto(
            id = freshId,
            title = "New Chat",
            timestamp = System.currentTimeMillis(),
            messages = listOf(defaultWelcomeMsg)
        )
        sessions.value = listOf(freshSession) + sessions.value
        currentSessionId.value = freshId
        messages.value = freshSession.messages
        DesktopChatStorage.saveSessions(sessions.value)
    }

    fun switchSession(sessionId: String) {
        val session = sessions.value.firstOrNull { it.id == sessionId } ?: return
        currentSessionId.value = session.id
        messages.value = session.messages.ifEmpty { listOf(defaultWelcomeMsg) }
    }

    fun deleteSession(sessionId: String) {
        val updated = sessions.value.filter { it.id != sessionId }
        sessions.value = updated
        DesktopChatStorage.saveSessions(updated)

        if (currentSessionId.value == sessionId) {
            if (updated.isNotEmpty()) {
                switchSession(updated.first().id)
            } else {
                createNewSession()
            }
        }
    }

    fun clearAllChatSessions() {
        DesktopChatStorage.clearAll()
        sessions.value = emptyList()
        createNewSession()
    }

    private fun generateSmartTitle(input: String): String {
        val clean = input.removePrefix("search ").removePrefix("find ").removePrefix("tell me about ").removePrefix("what is ").removePrefix("latest info on ").trim()
        val words = clean.split(" ").filter { it.isNotBlank() }.take(6)
        val title = words.joinToString(" ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (title.length > 42) title.substring(0, 40) + "..." else title.ifEmpty { "General Chat" }
    }

    private fun updateSessionTitle(sessionId: String, newTitle: String) {
        sessions.value = sessions.value.map { s ->
            if (s.id == sessionId) s.copy(title = newTitle) else s
        }
        DesktopChatStorage.saveSessions(sessions.value)
    }

    val meals = nutritionRepo.meals
    val dailySignals = healthRepo.signals
    val tasks = tasksRepo.tasks
    val goals = tasksRepo.goals
    val journalEntries = tasksRepo.journalEntries
    val healthArchiveRecords = healthArchiveRepo.records
    val reminders = tasksRepo.reminders

    val apiKey = MutableStateFlow("")
    val personaStyle = MutableStateFlow("Clean Assistant")
    val localModelName = MutableStateFlow("llama3.2:1b")
    val isPhoneConnected: StateFlow<Boolean> = syncClient.isConnected
    val ktorServer = DesktopKtorServer(8089)
    val phoneName: StateFlow<String?> = syncClient.peerName

    fun initSync() {
        syncClient.connect(
            onMealsReceived = { nutritionRepo.setMeals(it) },
            onTasksReceived = { tasksRepo.setTasks(it) },
            onRemindersReceived = { tasksRepo.setReminders(it) },
            onSignalReceived = { healthRepo.setSignals(it) },
            onRejected = { notice.value = it }
        )

        // Start Ktor WebSocket Server for wireless Wi-Fi and cloud client connections
        ktorServer.start { envelope ->
            when (val p = envelope.payload) {
                is MealsSnapshot -> nutritionRepo.setMeals(p.meals)
                is TasksSnapshot -> tasksRepo.setTasks(p.tasks)
                is RemindersSnapshot -> tasksRepo.setReminders(p.reminders)
                is SignalsSnapshot -> healthRepo.setSignals(p.signals)
                else -> Unit
            }
        }
    }

    private fun requiresWebSearch(text: String): Boolean {
        val cleanLower = text.lowercase().replace(Regex("""[^a-z0-9\s]"""), "").trim()

        // 1. Explicit user search requests
        if (cleanLower.startsWith("search ") || cleanLower.startsWith("find news") || cleanLower.startsWith("google ")) return true

        // 2. Real-time live data signals
        val liveSignals = listOf("latest news", "weather in", "stock price", "live score", "today news", "upcoming movie")
        return liveSignals.any { cleanLower.contains(it) }
    }

    fun sendMessage(userText: String) {
        val userMsg = ChatMessageDto("msg_${System.currentTimeMillis()}", ChatRole.USER, userText, System.currentTimeMillis())
        val updatedUserMsgs = messages.value + userMsg
        messages.value = updatedUserMsgs

        // Auto-title session if still named "New Chat"
        val currSession = sessions.value.firstOrNull { it.id == currentSessionId.value }
        if (currSession != null && (currSession.title == "New Chat" || currSession.title.isBlank())) {
            updateSessionTitle(currentSessionId.value, generateSmartTitle(userText))
        }

        saveCurrentSessionMessages()

        scope.launch {
            val botMsgId = "msg_${System.currentTimeMillis() + 1}"

            val initialBotMsg = ChatMessageDto(
                id = botMsgId,
                sender = ChatRole.ASSISTANT,
                text = "⚡ Analyzing prompt & initializing local LLM...",
                timestamp = System.currentTimeMillis()
            )
            messages.value = messages.value + initialBotMsg

            val lower = userText.lowercase().trim()

            val historyText = messages.value
                .filter { !it.text.startsWith("⚡") && !it.text.startsWith("🌐") }
                .takeLast(6)
                .joinToString("\n") { msg ->
                    val role = if (msg.sender == ChatRole.USER) "User" else "Assistant"
                    "$role: ${msg.text}"
                }

            val urlMatch = Regex("""https?://\S+""").find(userText)
            if (lower.startsWith("open ") && urlMatch != null) {
                val tool = orchestrator.getAvailableTools().firstOrNull { it.name == "open_browser" }
                if (tool != null) {
                    updateBotMessage(botMsgId, "${tool.execute(mapOf("url" to urlMatch.value))}.")
                    return@launch
                }
            }

            if (lower.startsWith("copy ")) {
                val tool = orchestrator.getAvailableTools().firstOrNull { it.name == "copy_clipboard" }
                if (tool != null) {
                    updateBotMessage(botMsgId, "${tool.execute(mapOf("text" to userText.substring(5).trim()))}.")
                    return@launch
                }
            }

            if (lower.contains("analyze") || lower.contains("analysis") || lower.contains("report") || lower.contains("audit")) {
                val report = when {
                    lower.contains("nutrition") || lower.contains("food") || lower.contains("calorie") || lower.contains("diet") -> analyzeNutritionData()
                    lower.contains("health") || lower.contains("sleep") || lower.contains("body") || lower.contains("recovery") -> analyzeHealthData()
                    lower.contains("task") || lower.contains("todo") || lower.contains("productivity") || lower.contains("goal") -> analyzeProductivityData()
                    else -> performFullExecutiveAudit()
                }
                updateBotMessage(botMsgId, report)
                return@launch
            }

            val needsWebSearch = requiresWebSearch(userText)

            val promptToUse = if (needsWebSearch) {
                val searchQuery = userText.removePrefix("search ").removePrefix("find ").removePrefix("tell me about ").removePrefix("what is ").removePrefix("latest info on ").trim()
                updateBotMessage(botMsgId, "🌐 Searching web for: \"$searchQuery\"...")
                val webResults = webSearchTool.execute(mapOf("query" to searchQuery.ifEmpty { userText }))
                updateBotMessage(botMsgId, "⚡ Synthesizing web results with local LLM...")
                """
                    Search Results:
                    $webResults
                    
                    User Query: $userText
                """.trimIndent()
            } else {
                if (historyText.isNotBlank()) {
                    "Conversation Context:\n$historyText\n\n$userText"
                } else {
                    userText
                }
            }

            // 2. Stream AI tokens continuously in real time!
            val sb = StringBuilder()
            val success = aiClient.generateStreamingResponse(
                userPrompt = promptToUse,
                apiKey = apiKey.value,
                personaStyle = personaStyle.value,
                localModelName = localModelName.value
            ) { token ->
                sb.append(token)
                updateBotMessage(botMsgId, sb.toString())
            }

            if (!success || sb.isBlank()) {
                val fallback = aiClient.generateFallbackResponse(userText, personaStyle.value)
                updateBotMessage(botMsgId, fallback)
            }
        }
    }

    private fun cleanAiFormatting(text: String): String {
        var clean = text
        clean = clean.replace(Regex("""(?s)^\s*I'm happy to help you with your questions.*?\n\n"""), "")
        clean = clean.replace(Regex("""(?m)^\s*(Recent Context:|Your Question:|Current Question:|Current User Query:|Conversation Context:).*$"""), "")
        clean = clean.replace(Regex("""\[([^\]]+)\]\([^\)]+\)"""), "$1")
        clean = clean.replace(Regex("""https?://\S+"""), "")
        clean = clean.replace(Regex("""(?m)^\s*Please note that this is a synthesis.*$"""), "")
        return clean.trim()
    }

    private fun updateBotMessage(msgId: String, newText: String) {
        val formattedText = if (newText.startsWith("⚡") || newText.startsWith("🌐")) newText else cleanAiFormatting(newText)
        messages.value = messages.value.map { msg ->
            if (msg.id == msgId) msg.copy(text = formattedText) else msg
        }
        saveCurrentSessionMessages()
    }

    private fun saveCurrentSessionMessages() {
        val cleanMsgs = messages.value.filter { !it.text.startsWith("⚡") && !it.text.startsWith("🌐") }
        sessions.value = sessions.value.map { s ->
            if (s.id == currentSessionId.value) s.copy(messages = cleanMsgs, timestamp = System.currentTimeMillis()) else s
        }
        DesktopChatStorage.saveSessions(sessions.value)
    }

    private fun analyzeNutritionData(): String {
        val today = meals.value.todayMeals()
        val kcal = today.sumOf { it.calories }
        val protein = today.sumOf { it.protein.toDouble() }.toInt()
        val carbs = today.sumOf { it.carbs.toDouble() }.toInt()
        val fat = today.sumOf { it.fat.toDouble() }.toInt()
        return """
            NUTRITION (today, from your phone)
            • Calories: $kcal kcal (reference goal $DEFAULT_KCAL_GOAL is a default, not your setting)
            • Macros: Protein ${protein}g | Carbs ${carbs}g | Fat ${fat}g
            • Meals logged today: ${today.size}
        """.trimIndent()
    }

    private fun analyzeHealthData(): String {
        val s = dailySignals.value
        val water = s.waterIntakeMl?.let { "$it ml (reference $DEFAULT_WATER_GOAL_ML ml)" } ?: "unknown"
        val screen = s.phoneUsageMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "unknown"
        return """
            HEALTH (only what the phone has sent)
            • Water today: $water
            • Phone screen time: $screen
            • Sleep, heart rate, steps, workouts: not sent by the phone yet
        """.trimIndent()
    }

    private fun analyzeProductivityData(): String {
        val pending = tasks.value.count { !it.isCompleted }
        val completed = tasks.value.count { it.isCompleted }
        val total = tasks.value.size
        return """
            TASKS (from your phone)
            • Pending: $pending
            • Completed: $completed of $total
        """.trimIndent()
    }

    private fun performFullExecutiveAudit(): String {
        val kcal = meals.value.todayMeals().sumOf { it.calories }
        val pending = tasks.value.count { !it.isCompleted }
        return """
            STATUS
            • Phone link: ${if (isPhoneConnected.value) "connected (USB, read-only)" else "offline"}
            • Calories today: $kcal kcal
            • Water today: ${dailySignals.value.waterIntakeMl?.let { "$it ml" } ?: "unknown"}
            • Pending tasks: $pending
        """.trimIndent()
    }

    /** Create a desktop-owned task and durably enqueue it for encrypted Drive sync. */
    fun addTask(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val now = System.currentTimeMillis()
        val id = Ids.randomHex(16)
        val isGoal = cleanTitle.startsWith("[GOAL]", ignoreCase = true)
        val recordTitle = if (isGoal) cleanTitle.removeRange(0, 6).trimStart(' ', ':', '-') else cleanTitle
        if (recordTitle.isEmpty()) return
        if (isGoal) {
            val payload = cloudPayloadJson.encodeToJsonElement(
                GoalSyncPayload.serializer(),
                GoalSyncPayload(
                    syncId = id,
                    title = recordTitle,
                    targetDate = null,
                    isDone = false,
                    createdAt = now
                )
            ).jsonObject
            enqueueDesktopUpsert(CloudSyncEntityTypes.GOAL, id, payload, "Goal")
        } else {
            val payload = cloudPayloadJson.encodeToJsonElement(
                TodoSyncPayload.serializer(),
                TodoSyncPayload(
                    syncId = id,
                    title = recordTitle,
                    isDone = false,
                    createdAt = now,
                    completedAt = null,
                    dueAt = null
                )
            ).jsonObject
            enqueueDesktopUpsert(CloudSyncEntityTypes.TODO, id, payload, "Task")
        }
    }

    /** Toggle the same globally unique task ID; never create a replacement task. */
    fun toggleTask(task: FridayTaskDto) {
        if (task.id.isBlank()) return
        val now = System.currentTimeMillis()
        val nextDone = !task.isCompleted
        val createdAt = task.createdTimestamp.takeIf { it > 0L }
            ?: task.timestamp.takeIf { it > 0L }
            ?: now
        val payload = cloudPayloadJson.encodeToJsonElement(
            TodoSyncPayload.serializer(),
            TodoSyncPayload(
                syncId = task.id,
                title = task.title,
                isDone = nextDone,
                createdAt = createdAt,
                completedAt = if (nextDone) now else null,
                dueAt = task.deadlineTimestamp
            )
        ).jsonObject
        enqueueDesktopUpsert(CloudSyncEntityTypes.TODO, task.id, payload, "Task update")
    }

    /** Toggle a cloud-backed goal without changing its stable sync identity. */
    fun toggleGoal(goal: GoalSyncPayload) {
        if (goal.syncId.isBlank()) return
        val updated = goal.copy(isDone = !goal.isDone)
        val payload = cloudPayloadJson.encodeToJsonElement(
            GoalSyncPayload.serializer(), updated
        ).jsonObject
        enqueueDesktopUpsert(CloudSyncEntityTypes.GOAL, goal.syncId, payload, "Goal update")
    }

    /** Desktop meal entries use the shared sync ID, not a local database row ID. */
    fun logMeal(name: String, calories: Int, protein: Float, carbs: Float, fat: Float) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || calories <= 0) return
        val now = System.currentTimeMillis()
        val id = Ids.randomHex(16)
        val payload = cloudPayloadJson.encodeToJsonElement(
            MealSyncPayload.serializer(),
            MealSyncPayload(
                syncId = id,
                name = cleanName,
                mealType = "meal",
                calories = calories,
                protein = protein.toDouble().coerceAtLeast(0.0),
                carbohydrates = carbs.toDouble().coerceAtLeast(0.0),
                fat = fat.toDouble().coerceAtLeast(0.0),
                timestamp = now,
                isEstimated = false,
                nutrientsJson = "{}"
            )
        ).jsonObject
        enqueueDesktopUpsert(CloudSyncEntityTypes.MEAL, id, payload, "Meal")
    }

    /** Water logs are individual events, so retries cannot double-add an aggregate. */
    fun addWater(amountMl: Int) {
        if (amountMl <= 0) return
        val now = System.currentTimeMillis()
        val id = Ids.randomHex(16)
        val payload = cloudPayloadJson.encodeToJsonElement(
            WaterSyncPayload.serializer(),
            WaterSyncPayload(syncId = id, amountMl = amountMl, timestamp = now)
        ).jsonObject
        enqueueDesktopUpsert(CloudSyncEntityTypes.WATER_ENTRY, id, payload, "Water entry")
    }

    /** Save a desktop journal entry into the same encrypted entity stream as phone entries. */
    fun addJournalEntry(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val now = System.currentTimeMillis()
        val id = Ids.randomHex(16)
        val payload = cloudPayloadJson.encodeToJsonElement(
            JournalSyncPayload.serializer(),
            JournalSyncPayload(
                syncId = id,
                text = cleanText,
                feedback = null,
                createdAt = now
            )
        ).jsonObject
        enqueueDesktopUpsert(CloudSyncEntityTypes.JOURNAL_ENTRY, id, payload, "Journal entry")
    }

    fun deleteTask(taskId: String) = enqueueDesktopDelete(CloudSyncEntityTypes.TODO, taskId, "Task deletion")
    fun deleteMeal(mealId: String) = enqueueDesktopDelete(CloudSyncEntityTypes.MEAL, mealId, "Meal deletion")
    fun deleteWaterEntry(entryId: String) = enqueueDesktopDelete(CloudSyncEntityTypes.WATER_ENTRY, entryId, "Water deletion")
    fun addReminder(title: String = "", timeMillis: Long = 0L) = readOnly("Adding a reminder")

    private val cloudPayloadJson = Json { encodeDefaults = true }

    private fun enqueueDesktopUpsert(
        entityType: String,
        entityId: String,
        payload: JsonObject,
        label: String
    ) {
        scope.launch {
            runCatching {
                cloudSyncRuntime.store.enqueueLocalUpsert(entityType, entityId, payload)
            }.onFailure { error ->
                notice.value = "$label could not be saved to FRIDAY's encrypted sync store: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    private fun enqueueDesktopDelete(entityType: String, entityId: String, label: String) {
        if (entityId.isBlank()) return
        scope.launch {
            runCatching {
                cloudSyncRuntime.store.enqueueLocalDelete(entityType, entityId)
            }.onFailure { error ->
                notice.value = "$label could not be saved to FRIDAY's encrypted sync store: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }
    fun updateSettings(newKey: String, newPersona: String, newModel: String = "llama3.2:1b") {
        apiKey.value = newKey
        personaStyle.value = newPersona
        localModelName.value = newModel
    }

    fun connectGoogleDrive(credentialsFile: File): String =
        cloudSyncRuntime.recoveryService.connect(credentialsFile)

    fun backUpGoogleDriveRecoveryKey(passphrase: CharArray): String =
        cloudSyncRuntime.recoveryService.backUpRecoveryKey(passphrase)

    fun restoreGoogleDriveRecoveryKey(passphrase: CharArray): String =
        cloudSyncRuntime.recoveryService.restoreRecoveryKey(passphrase)

    fun syncGoogleDrive(): DesktopDriveSyncResult =
        kotlinx.coroutines.runBlocking { cloudSyncRuntime.syncEngine.performSync() }

    fun disconnect() {
        syncClient.disconnect()
    }
}
