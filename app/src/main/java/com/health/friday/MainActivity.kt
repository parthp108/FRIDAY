
package com.health.friday

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import com.health.friday.ai.AiMessage
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ai.AiToolRegistry
import com.health.friday.ai.GeminiAiClient
import com.health.friday.ai.LocalAiClient
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
import com.health.friday.journal.JournalRepository
import com.health.friday.journal.JournalScreen
import com.health.friday.nutrition.GetTodayNutritionTool
import com.health.friday.nutrition.GetWeekNutritionTool
import com.health.friday.nutrition.LocalNutritionProvider
import com.health.friday.nutrition.LogEstimatedMealTool
import com.health.friday.nutrition.LogMealTool
import com.health.friday.nutrition.LogWaterTool
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.NutritionScreen
import com.health.friday.nutrition.WaterRepository
import com.health.friday.reminders.ReminderRepository
import com.health.friday.reminders.ReminderScheduler
import com.health.friday.reminders.ReminderTool
import com.health.friday.settings.SettingsRepository
import com.health.friday.settings.SettingsScreen
import com.health.friday.tasks.GoalRepository
import com.health.friday.tasks.GoalTool
import com.health.friday.tasks.TasksScreen
import com.health.friday.tasks.TodoRepository
import com.health.friday.tasks.TodoTool
import com.health.friday.ui.theme.FRIDAYTheme
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

    override fun onResume() {
        super.onResume()

        resumeCount.intValue =
            resumeCount.intValue + 1
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

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

        val database =
            FridayDatabase.getDatabase(
                this
            )

        val settingsRepository =
            SettingsRepository(this)

        val healthConnectRepository =
            HealthConnectRepository(this)

        val nutritionProvider =
            LocalNutritionProvider()

        val nutritionRepository =
            NutritionRepository(
                mealDao =
                    database.mealDao(),

                nutritionProvider =
                    nutritionProvider,

                healthConnectRepository =
                    healthConnectRepository
            )

        val waterRepository =
            WaterRepository(
                waterDao =
                    database.waterDao(),

                healthConnectRepository =
                    healthConnectRepository
            )

        val todoRepository =
            TodoRepository(
                todoDao =
                    database.todoDao()
            )

        val goalRepository =
            GoalRepository(
                goalDao =
                    database.goalDao()
            )

        val journalRepository =
            JournalRepository(
                journalDao =
                    database.journalDao()
            )

        val chatRepository =
            ChatRepository(
                conversationDao =
                    database.chatConversationDao(),

                messageDao =
                    database.chatMessageDao()
            )

        val usageRepository =
            DeviceUsageRepository(this)

        val reminderScheduler =
            ReminderScheduler(this)

        val reminderRepository =
            ReminderRepository(
                reminderDao =
                    database.reminderDao(),

                scheduler =
                    reminderScheduler
            )

        val alarmScheduler =
            AlarmScheduler(this)

        val alarmRepository =
            AlarmRepository(
                alarmDao =
                    database.alarmDao(),

                scheduler =
                    alarmScheduler
            )

        val aiToolRegistry =
            AiToolRegistry(
                tools =
                    listOf(
                        LogMealTool(
                            nutritionRepository
                        ),

                        LogEstimatedMealTool(
                            nutritionRepository
                        ),

                        LogWaterTool(
                            waterRepository
                        ),

                        GetTodayNutritionTool(
                            repository =
                                nutritionRepository,

                            waterRepository =
                                waterRepository
                        ),

                        GetWeekNutritionTool(
                            repository =
                                nutritionRepository,

                            waterRepository =
                                waterRepository
                        ),

                        TodoTool(
                            todoRepository
                        ),

                        GoalTool(
                            goalRepository
                        ),

                        ReminderTool(
                            reminderRepository
                        ),

                        AlarmTool(
                            alarmRepository
                        )
                    )
            )

        val localClient =
            LocalAiClient()

        val geminiClient =
            GeminiAiClient(
                settings =
                    settingsRepository,

                toolRegistry =
                    aiToolRegistry
            )

        val aiClient =
            RoutingAiClient(
                settings =
                    settingsRepository,

                gemini =
                    geminiClient,

                local =
                    localClient
            )

        val aiOrchestrator =
            AiOrchestrator(
                aiClient =
                    aiClient,

                toolRegistry =
                    aiToolRegistry
            )

        setContent {

            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {

                val usageRefreshKey =
                    resumeCount.intValue

                FridayApp(
                    nutritionRepository =
                        nutritionRepository,

                    waterRepository =
                        waterRepository,

                    healthConnectRepository =
                        healthConnectRepository,

                    todoRepository =
                        todoRepository,

                    goalRepository =
                        goalRepository,

                    journalRepository =
                        journalRepository,

                    chatRepository =
                        chatRepository,

                    usageRepository =
                        usageRepository,

                    settingsRepository =
                        settingsRepository,

                    usageRefreshKey =
                        usageRefreshKey,

                    aiOrchestrator =
                        aiOrchestrator
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

    usageRefreshKey:
    Int,

    aiOrchestrator:
    AiOrchestrator
) {

    var selectedScreen by remember {
        mutableStateOf(0)
    }

    var showAssistant by remember {
        mutableStateOf(false)
    }

    var showHistory by remember {
        mutableStateOf(false)
    }

    var showJournal by remember {
        mutableStateOf(false)
    }

    var showSaveChatDialog by remember {
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

        showSaveChatDialog =
            false

        showAssistant =
            false

        assistantMessages =
            emptyList()

        restoredMessages =
            emptyList()

        activeConversationId =
            null

        aiOrchestrator.clearConversation()
    }

    fun requestCloseAssistant() {

        val hasUserMessage =
            assistantMessages.any {
                it.role == "user" &&
                        it.content.isNotBlank()
            }

        if (hasUserMessage) {

            showSaveChatDialog =
                true

        } else {

            closeWithoutSaving()
        }
    }

    fun saveAndCloseAssistant() {

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
                    content = it.content
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

            closeWithoutSaving()
        }
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
                            it.content
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

    if (showSaveChatDialog) {

        AlertDialog(
            onDismissRequest = {
                showSaveChatDialog =
                    false
            },

            title = {
                Text(
                    "Save this conversation?"
                )
            },

            text = {
                Text(
                    "You decide whether this FRIDAY conversation is saved."
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        saveAndCloseAssistant()
                    }
                ) {
                    Text("Save")
                }
            },

            dismissButton = {

                Row {

                    TextButton(
                        onClick = {
                            closeWithoutSaving()
                        }
                    ) {
                        Text("Don't save")
                    }

                    TextButton(
                        onClick = {
                            showSaveChatDialog =
                                false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showHistory) {

        BackHandler {
            showHistory =
                false
        }

        ChatHistoryScreen(
            repository =
                chatRepository,

            onBack = {
                showHistory =
                    false
            },

            onOpenConversation = {
                openSavedConversation(it)
            }
        )

    } else if (showAssistant) {

        BackHandler {
            requestCloseAssistant()
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
                            requestCloseAssistant()
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
                orchestrator =
                    aiOrchestrator,

                initialMessages =
                    restoredMessages,

                onMessagesChanged = {
                    assistantMessages =
                        it
                },

                onOpenHistory = {
                    showHistory =
                        true
                },

                modifier =
                    Modifier
                        .padding(
                            innerPadding
                        )
                        .imePadding()
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
                "Health",
                "Nutrition",
                "Tasks",
                "Settings"
            )

        val icons =
            listOf(
                "⌂",
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
                    HealthScreen(
                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )

                2 ->
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

                3 ->
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
