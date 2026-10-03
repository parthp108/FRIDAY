package com.health.friday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ai.AiToolRegistry
import com.health.friday.ai.GeminiAiClient
import com.health.friday.ai.LocalAiClient
import com.health.friday.ai.RoutingAiClient
import com.health.friday.assistant.AssistantScreen
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
import com.health.friday.nutrition.LogMealTool
import com.health.friday.nutrition.LogWaterTool
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.NutritionScreen
import com.health.friday.nutrition.WaterRepository
import com.health.friday.settings.SettingsRepository
import com.health.friday.settings.SettingsScreen
import com.health.friday.tasks.GoalRepository
import com.health.friday.tasks.TasksScreen
import com.health.friday.tasks.TodoRepository
import com.health.friday.ui.theme.FRIDAYTheme
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText

class MainActivity : ComponentActivity() {

    // Goes up every time the app comes to the foreground, so screens
    // (like the phone usage card) can reload after you return from Settings.
    private val resumeCount = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount.intValue = resumeCount.intValue + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val database =
            FridayDatabase.getDatabase(this)

        val settingsRepository =
            SettingsRepository(this)

        // ---------------------------------------------------------
        // HEALTH CONNECT
        // ---------------------------------------------------------

        val healthConnectRepository =
            HealthConnectRepository(this)

        // ---------------------------------------------------------
        // NUTRITION
        // ---------------------------------------------------------

        val nutritionProvider =
            LocalNutritionProvider()

        val nutritionRepository =
            NutritionRepository(
                mealDao = database.mealDao(),
                nutritionProvider = nutritionProvider,
                healthConnectRepository = healthConnectRepository
            )

        val waterRepository =
            WaterRepository(
                waterDao = database.waterDao(),
                healthConnectRepository = healthConnectRepository
            )

        // ---------------------------------------------------------
        // TASKS / GOALS / JOURNAL
        // ---------------------------------------------------------

        val todoRepository =
            TodoRepository(
                todoDao = database.todoDao()
            )

        val goalRepository =
            GoalRepository(
                goalDao = database.goalDao()
            )

        val journalRepository =
            JournalRepository(
                journalDao = database.journalDao()
            )

        // ---------------------------------------------------------
        // DEVICE USAGE
        // ---------------------------------------------------------

        val usageRepository =
            DeviceUsageRepository(this)

        // ---------------------------------------------------------
        // AI TOOLS
        // ---------------------------------------------------------

        val aiToolRegistry =
            AiToolRegistry(
                tools = listOf(
                    LogMealTool(nutritionRepository),
                    LogWaterTool(waterRepository),
                    GetTodayNutritionTool(
                        repository = nutritionRepository,
                        waterRepository = waterRepository
                    ),
                    GetWeekNutritionTool(
                        repository = nutritionRepository,
                        waterRepository = waterRepository
                    )
                )
            )

        // ---------------------------------------------------------
        // AI CLIENTS
        // ---------------------------------------------------------

        val localClient =
            LocalAiClient()

        val geminiClient =
            GeminiAiClient(
                settings = settingsRepository,
                toolRegistry = aiToolRegistry
            )

        val aiClient =
            RoutingAiClient(
                settings = settingsRepository,
                gemini = geminiClient,
                local = localClient
            )

        val aiOrchestrator =
            AiOrchestrator(
                aiClient = aiClient,
                toolRegistry = aiToolRegistry
            )

        // ---------------------------------------------------------
        // UI
        // ---------------------------------------------------------

        setContent {

            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {

                val usageRefreshKey =
                    resumeCount.intValue

                FridayApp(
                    nutritionRepository = nutritionRepository,
                    waterRepository = waterRepository,
                    todoRepository = todoRepository,
                    goalRepository = goalRepository,
                    journalRepository = journalRepository,
                    usageRepository = usageRepository,
                    settingsRepository = settingsRepository,
                    usageRefreshKey = usageRefreshKey,
                    aiOrchestrator = aiOrchestrator
                )
            }
        }
    }
}

@Composable
fun FridayApp(
    nutritionRepository: NutritionRepository,
    waterRepository: WaterRepository,
    todoRepository: TodoRepository,
    goalRepository: GoalRepository,
    journalRepository: JournalRepository,
    usageRepository: DeviceUsageRepository,
    settingsRepository: SettingsRepository,
    usageRefreshKey: Int,
    aiOrchestrator: AiOrchestrator
) {

    var selectedScreen by remember {
        mutableStateOf(0)
    }

    var showAssistant by remember {
        mutableStateOf(false)
    }

    var showJournal by remember {
        mutableStateOf(false)
    }

    val closeAssistant: () -> Unit = {
        showAssistant = false
        aiOrchestrator.clearConversation()
    }

    if (showAssistant) {

        BackHandler {
            closeAssistant()
        }

        Scaffold(

            containerColor = FridayBackground,
            contentColor = FridayText,

            topBar = {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {

                    TextButton(
                        onClick = closeAssistant
                    ) {
                        Text(
                            text = "Close",
                            color = FridayCyan
                        )
                    }
                }
            }

        ) { innerPadding ->

            AssistantScreen(
                orchestrator = aiOrchestrator,
                modifier = Modifier
                    .padding(innerPadding)
                    .imePadding()
            )
        }

    } else if (showJournal) {

        BackHandler {
            showJournal = false
        }

        Scaffold(

            containerColor = FridayBackground,
            contentColor = FridayText

        ) { innerPadding ->

            JournalScreen(
                repository = journalRepository,
                onBack = {
                    showJournal = false
                },
                modifier = Modifier
                    .padding(innerPadding)
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

            containerColor = FridayBackground,
            contentColor = FridayText,

            floatingActionButton = {

                ExtendedFloatingActionButton(
                    onClick = {
                        showAssistant = true
                    },
                    containerColor = FridayCyan,
                    contentColor = FridayBackground
                ) {
                    Text(
                        text = "FRIDAY",
                        fontWeight = FontWeight.Bold
                    )
                }
            },

            bottomBar = {

                NavigationBar(
                    containerColor = FridayCard
                ) {

                    screens.forEachIndexed { index, screen ->

                        NavigationBarItem(

                            selected =
                                selectedScreen == index,

                            onClick = {
                                selectedScreen = index
                            },

                            icon = {
                                Text(text = icons[index])
                            },

                            label = {
                                Text(screen)
                            },

                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = FridayCyan,
                                    selectedTextColor = FridayCyan,
                                    indicatorColor = FridayCardLight,
                                    unselectedIconColor = FridayMuted,
                                    unselectedTextColor = FridayMuted
                                )
                        )
                    }
                }
            }

        ) { innerPadding ->

            when (selectedScreen) {

                0 -> HomeScreen(
                    repository = nutritionRepository,
                    waterRepository = waterRepository,
                    modifier = Modifier.padding(innerPadding)
                )

                1 -> HealthScreen(
                    modifier = Modifier.padding(innerPadding)
                )

                2 -> NutritionScreen(
                    repository = nutritionRepository,
                    waterRepository = waterRepository,
                    modifier = Modifier.padding(innerPadding)
                )

                3 -> TasksScreen(
                    todoRepository = todoRepository,
                    goalRepository = goalRepository,
                    usageRepository = usageRepository,
                    usageRefreshKey = usageRefreshKey,
                    onOpenJournal = {
                        showJournal = true
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                else -> SettingsScreen(
                    settings = settingsRepository,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}