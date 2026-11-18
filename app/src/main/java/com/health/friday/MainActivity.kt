package com.health.friday

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.friday.ai.AiMessage
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ai.AiToolRegistry
import com.health.friday.ai.GeminiAiClient
import com.health.friday.ai.RoutingAiClient
import com.health.friday.alarms.AlarmRepository
import com.health.friday.alarms.AlarmScheduler
import com.health.friday.alarms.AlarmTool
import com.health.friday.assistant.AssistantScreen
import com.health.friday.chat.ChatRepository
import com.health.friday.data.local.ChatConversation
import com.health.friday.data.local.ChatMessage
import com.health.friday.data.local.FridayDatabase
import com.health.friday.device.DeviceUsageRepository
import com.health.friday.device.HealthConnectRepository
import com.health.friday.health.HealthScreen
import com.health.friday.home.HomeScreen
import com.health.friday.loom.LoomRoute
import com.health.friday.journal.JournalRepository
import com.health.friday.journal.JournalScreen
import com.health.friday.nutrition.GetTodayNutritionTool
import com.health.friday.nutrition.GetWeekNutritionTool
import com.health.friday.nutrition.LocalNutritionProvider
import com.health.friday.nutrition.LogEstimatedMealTool
import com.health.friday.nutrition.LogWaterTool
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.NutritionScreen
import com.health.friday.nutrition.WaterRepository
import com.health.friday.reminders.ReminderRepository
import com.health.friday.reminders.ReminderScheduler
import com.health.friday.reminders.ReminderTool
import com.health.friday.agentic.SystemController
import com.health.friday.assistant.AssistantRoleManager
import com.health.friday.diagnostics.FridayDiagnosticScreen
import com.health.friday.settings.SettingsRepository
import com.health.friday.settings.SettingsScreen
import com.health.friday.tasks.GoalRepository
import com.health.friday.tasks.GoalTool
import com.health.friday.tasks.TasksScreen
import com.health.friday.tasks.TodoRepository
import com.health.friday.tasks.TodoTool
import com.health.friday.ui.theme.FRIDAYTheme
import com.health.friday.voice.FridaySpeechRecognizer
import com.health.friday.voice.FridayTtsEngine
import com.health.friday.telemetry.HealthSyncManager
import com.health.friday.telemetry.HealthTelemetryWorker
import com.health.friday.proactive.EveningCheckinWorker
import com.health.friday.proactive.DaytimeWaterWorker
import com.health.friday.proactive.ProactiveMealWorker
import com.health.friday.proactive.ProactiveBedtimeJournalWorker
import com.health.friday.proactive.ProactiveSleepGuardWorker
import com.health.friday.proactive.ProactiveWorkoutWorker
import com.health.friday.proactive.SurpriseIntelligenceWorker
import com.health.friday.proactive.FridayForegroundService
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val resumeCount =
        mutableIntStateOf(0)
    private val isAssistLaunch =
        mutableStateOf(false)

    override fun onResume() {
        super.onResume()

        resumeCount.intValue =
            resumeCount.intValue + 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAssistIntent(intent)
    }

    private fun checkAssistIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_MAIN) {
            isAssistLaunch.value = false
            intent.removeExtra("open_assistant")
            return
        }
        val isOpenAssistant = intent.getBooleanExtra("open_assistant", false)

        val isAssistAction = (action == Intent.ACTION_ASSIST ||
                action == Intent.ACTION_VOICE_COMMAND ||
                action == "android.intent.action.SEARCH_LONG_PRESS" ||
                isOpenAssistant) && action != Intent.ACTION_MAIN

        if (isAssistAction) {
            isAssistLaunch.value = true
            intent.action = null
            intent.removeExtra("open_assistant")
        } else {
            isAssistLaunch.value = false
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )
        checkAssistIntent(intent)
        AssistantRoleManager.enforceAssistantBindingViaShizuku(this)

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ),
                1001
            )
        }

        enableEdgeToEdge()

        val appContainer = (applicationContext as FridayApplication).container


        // Eagerly pre-load Gemma local LLM in background if enabled
        appContainer.localLlmClient.preloadModel()

        // Ambient Audio Mesh Foreground Service ("Hey Friday" wakeword listener) - Disabled by default to save battery
        // To launch on demand or if enabled in settings:
        // val fgIntent = Intent(this, FridayForegroundService::class.java)
        // startForegroundService(fgIntent)

        // PHASE 8: Start Background Telemetry Sync & Proactive Workers
        HealthTelemetryWorker.schedulePeriodic(this)
        HealthTelemetryWorker.runNow(this)
        EveningCheckinWorker.runNow(this)
        DaytimeWaterWorker.schedule(this)
        ProactiveMealWorker.schedule(this)
        ProactiveBedtimeJournalWorker.schedule(this)
        ProactiveSleepGuardWorker.schedule(this)
        ProactiveWorkoutWorker.schedule(this)
        SurpriseIntelligenceWorker.schedule(this)

        setContent {

            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {

                val usageRefreshKey =
                    resumeCount.intValue

                FridayApp(
                    nutritionRepository =
                        appContainer.nutritionRepository,

                    waterRepository =
                        appContainer.waterRepository,

                    healthConnectRepository =
                        appContainer.healthConnectRepository,

                    todoRepository =
                        appContainer.todoRepository,

                    goalRepository =
                        appContainer.goalRepository,

                    journalRepository =
                        appContainer.journalRepository,

                    chatRepository =
                        appContainer.chatRepository,

                    usageRepository =
                        appContainer.usageRepository,

                    settingsRepository =
                        appContainer.settingsRepository,

                    alarmRepository =
                        appContainer.alarmRepository,

                    systemController =
                        appContainer.systemController,

                    usageRefreshKey =
                        usageRefreshKey,

                    aiOrchestrator =
                        appContainer.aiOrchestrator,

                    speechRecognizer =
                        appContainer.speechRecognizer,

                    ttsEngine =
                        appContainer.ttsEngine,

                    isAssistLaunch =
                        isAssistLaunch.value
                )
            }
        }
    }
}

@Composable
fun FridayApp(
    nutritionRepository:
    NutritionRepository,

    waterRepository:
    WaterRepository,

    healthConnectRepository:
    HealthConnectRepository,

    todoRepository:
    TodoRepository,

    goalRepository:
    GoalRepository,

    journalRepository:
    JournalRepository,

    chatRepository:
    ChatRepository,

    usageRepository:
    DeviceUsageRepository,

    settingsRepository:
    SettingsRepository,

    alarmRepository:
    AlarmRepository,

    systemController:
    SystemController,

    usageRefreshKey:
    Int,

    aiOrchestrator:
    AiOrchestrator,
    speechRecognizer: FridaySpeechRecognizer,
    ttsEngine: FridayTtsEngine,
    isAssistLaunch: Boolean = false
) {

    var selectedScreen by remember {
        mutableStateOf(0)
    }

    var showAssistant by remember(isAssistLaunch) {
        mutableStateOf(isAssistLaunch)
    }

    LaunchedEffect(isAssistLaunch) {
        if (isAssistLaunch) {
            showAssistant = true
        } else {
            showAssistant = false
            selectedScreen = 0
        }
    }

    var showHistory by remember {
        mutableStateOf(false)
    }

    var showJournal by remember {
        mutableStateOf(false)
    }

    var assistantMessages by remember {
        mutableStateOf<List<AiMessage>>(
            emptyList()
        )
    }

    var restoredMessages by remember {
        mutableStateOf<List<AiMessage>>(
            emptyList()
        )
    }

    var activeConversationId by remember {
        mutableStateOf<Long?>(null)
    }

    val scope =
        rememberCoroutineScope()

    fun closeWithoutSaving() {

        showAssistant =
            false

        showHistory =
            false

        assistantMessages =
            emptyList()

        restoredMessages =
            emptyList()

        activeConversationId =
            null

        aiOrchestrator.clearConversation()
    }

    fun saveConversation() {

        val userMessage =
            assistantMessages.firstOrNull {
                it.role == "user" &&
                        it.content.isNotBlank()
            }

        val title =
            userMessage
                ?.content
                ?.trim()
                ?.let {
                    if (it.length > 50) {
                        it.take(50) + "…"
                    } else {
                        it
                    }
                }
                ?: "FRIDAY conversation"

        val storedMessages =
            assistantMessages.map {

                ChatMessage(
                    conversationId = 0,
                    role = it.role,
                    content = it.content,
                    createdAt = it.createdAt
                )
            }

        scope.launch {

            chatRepository.saveConversation(
                title =
                    title,

                messages =
                    storedMessages,

                conversationId =
                    activeConversationId
            )
        }
    }

    fun returnFromHistory() {

        restoredMessages =
            assistantMessages

        showHistory =
            false
    }

    fun openSavedConversation(
        conversation:
        ChatConversation
    ) {

        scope.launch {

            val storedMessages =
                chatRepository.getMessages(
                    conversationId =
                        conversation.id
                )

            val messages =
                storedMessages.map {

                    AiMessage(
                        role =
                            it.role,

                        content =
                            it.content,

                        createdAt =
                            it.createdAt
                    )
                }

            aiOrchestrator.restoreConversation(
                messages
            )

            restoredMessages =
                messages

            assistantMessages =
                messages

            activeConversationId =
                conversation.id

            showHistory =
                false

            showAssistant =
                true
        }
    }

    if (showHistory) {

        BackHandler {
            returnFromHistory()
        }

        ChatHistoryScreen(
            repository =
                chatRepository,

            onBack = {
                returnFromHistory()
            },

            onOpenConversation = {
                openSavedConversation(it)
            }
        )

    } else if (showAssistant) {

        BackHandler {
            closeWithoutSaving()
        }

        Scaffold(
            containerColor =
                FridayBackground,

            contentColor =
                FridayText,

            topBar = {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                horizontal = 8.dp
                            ),

                    horizontalArrangement =
                        Arrangement.End
                ) {

                    TextButton(
                        onClick = {
                            saveConversation()
                        },

                        enabled =
                            assistantMessages.any {
                                it.role == "user" &&
                                        it.content.isNotBlank()
                            }
                    ) {

                        Text(
                            text = "Save",
                            color =
                                FridayCyan
                        )
                    }

                    TextButton(
                        onClick = {
                            closeWithoutSaving()
                        }
                    ) {

                        Text(
                            text = "Close",
                            color =
                                FridayCyan
                        )
                    }
                }
            }

        ) { innerPadding ->

            AssistantScreen(
                orchestrator = aiOrchestrator,
                speechRecognizer = speechRecognizer,
                ttsEngine = ttsEngine,
                initialMessages = restoredMessages,
                autoStartLiveChat = false,
                onMessagesChanged = { newMessages: List<AiMessage> -> assistantMessages = newMessages },
                onOpenHistory = { showHistory = true },
                modifier = Modifier.padding(innerPadding).imePadding()
            )
        }

    } else if (showJournal) {

        BackHandler {
            showJournal =
                false
        }

        Scaffold(
            containerColor =
                FridayBackground,

            contentColor =
                FridayText

        ) { innerPadding ->

            JournalScreen(
                repository =
                    journalRepository,

                onBack = {
                    showJournal =
                        false
                },

                onSendWithFeedback = {
                        entryText ->

                    val entry =
                        journalRepository.saveEntry(
                            entryText
                        )
                            ?: throw IllegalArgumentException(
                                "Journal entry is empty"
                            )

                    val feedback =
                        aiOrchestrator
                            .generateJournalFeedback(
                                journalText =
                                    entryText
                            )

                    journalRepository.attachFeedback(
                        entry =
                            entry,

                        feedback =
                            feedback
                    )
                },

                modifier =
                    Modifier
                        .padding(
                            innerPadding
                        )
                        .imePadding()
            )
        }

    } else {

        val screens =
            listOf(
                "Home",
                "Loom",
                "Health",
                "Nutrition",
                "Tasks",
                "Settings"
            )

        val icons =
            listOf(
                "⌂",
                "🧵",
                "♥",
                "🍽",
                "✓",
                "⚙"
            )

        Scaffold(
            containerColor =
                FridayBackground,

            contentColor =
                FridayText,

            floatingActionButton = {

                ExtendedFloatingActionButton(
                    onClick = {

                        restoredMessages =
                            emptyList()

                        assistantMessages =
                            emptyList()

                        activeConversationId =
                            null

                        aiOrchestrator
                            .clearConversation()

                        showAssistant =
                            true
                    },

                    containerColor =
                        FridayCyan,

                    contentColor =
                        FridayBackground
                ) {

                    Text(
                        text = "FRIDAY",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            },

            bottomBar = {

                NavigationBar(
                    containerColor =
                        FridayCard
                ) {

                    screens.forEachIndexed {
                            index,
                            screen ->

                        NavigationBarItem(
                            selected =
                                selectedScreen ==
                                        index,

                            onClick = {
                                selectedScreen =
                                    index
                            },

                            icon = {
                                Text(
                                    text =
                                        icons[index]
                                )
                            },

                            label = {
                                Text(
                                    screen
                                )
                            },

                            colors =
                                NavigationBarItemDefaults
                                    .colors(
                                        selectedIconColor =
                                            FridayCyan,

                                        selectedTextColor =
                                            FridayCyan,

                                        indicatorColor =
                                            FridayCardLight,

                                        unselectedIconColor =
                                            FridayMuted,

                                        unselectedTextColor =
                                            FridayMuted
                                    )
                        )
                    }
                }
            }

        ) { innerPadding ->

            when (selectedScreen) {

                0 ->
                    HomeScreen(
                        repository =
                            nutritionRepository,

                        waterRepository =
                            waterRepository,

                        todoRepository =
                            todoRepository,

                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )

                1 ->
                    LoomRoute(
                        modifier = Modifier.padding(innerPadding)
                    )

                2 ->
                    HealthScreen(
                        repository =
                            healthConnectRepository,
                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )

                3 ->
                    NutritionScreen(
                        repository =
                            nutritionRepository,

                        waterRepository =
                            waterRepository,

                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )

                4 ->
                    TasksScreen(
                        todoRepository =
                            todoRepository,

                        goalRepository =
                            goalRepository,

                        usageRepository =
                            usageRepository,

                        usageRefreshKey =
                            usageRefreshKey,

                        onOpenJournal = {
                            showJournal =
                                true
                        },

                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )

                else ->
                    SettingsScreen(
                        settings =
                            settingsRepository,
                        alarmRepository =
                            alarmRepository,
                        systemController =
                            systemController,
                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )
            }
        }
    }
}

@Composable
private fun ChatHistoryScreen(
    repository:
    ChatRepository,

    onBack:
        () -> Unit,

    onOpenConversation:
        (ChatConversation) -> Unit
) {

    val conversations by
    repository
        .getConversations()
        .collectAsState(
            initial = emptyList()
        )

    val scope =
        rememberCoroutineScope()

    val dateFormat =
        remember {

            SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                Locale.getDefault()
            )
        }

    Scaffold(
        containerColor =
            FridayBackground,

        contentColor =
            FridayText

    ) { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        FridayBackground
                    )
                    .padding(
                        innerPadding
                    )
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            start = 8.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 8.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                TextButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        text = "Back",
                        color =
                            FridayCyan
                    )
                }

                Text(
                    text = "Chat History",
                    color =
                        FridayText,

                    fontWeight =
                        FontWeight.Bold,

                    modifier =
                        Modifier.padding(
                            start = 8.dp
                        )
                )
            }

            if (conversations.isEmpty()) {

                Text(
                    text =
                        "No saved conversations.",

                    color =
                        FridayMuted,

                    modifier =
                        Modifier.padding(
                            20.dp
                        )
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),

                    contentPadding =
                        PaddingValues(
                            16.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    items(
                        conversations,
                        key = {
                            it.id
                        }
                    ) { conversation ->

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),

                            colors =
                                CardDefaults
                                    .cardColors(
                                        containerColor =
                                            FridayCard
                                    ),

                            onClick = {
                                onOpenConversation(
                                    conversation
                                )
                            }
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        16.dp
                                    )
                            ) {

                                Text(
                                    text =
                                        conversation.title,

                                    color =
                                        FridayText,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Text(
                                    text =
                                        dateFormat.format(
                                            Date(
                                                conversation.updatedAt
                                            )
                                        ),

                                    color =
                                        FridayMuted,

                                    modifier =
                                        Modifier.padding(
                                            top = 5.dp
                                        )
                                )

                                TextButton(
                                    onClick = {

                                        scope.launch {

                                            repository
                                                .deleteConversation(
                                                    conversation
                                                )
                                        }
                                    }
                                ) {

                                    Text(
                                        text =
                                            "Delete",

                                        color =
                                            FridayMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
