package com.health.friday.di

import android.content.Context
import com.health.friday.actions.ActionRecorder
import com.health.friday.actions.UndoLastActionTool
import com.health.friday.ai.AiClient
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ai.AiToolRegistry
import com.health.friday.ai.GeminiAiClient
import com.health.friday.ai.LocalLlmClient
import com.health.friday.ai.RoutingAiClient
import com.health.friday.alarms.AlarmRepository
import com.health.friday.alarms.AlarmScheduler
import com.health.friday.alarms.AlarmTool
import com.health.friday.chat.ChatRepository
import com.health.friday.data.local.FridayDatabase
import com.health.friday.device.DeviceUsageRepository
import com.health.friday.device.HealthConnectRepository
import com.health.friday.journal.JournalRepository
import com.health.friday.memory.MemoryTool
import com.health.friday.nutrition.LogEstimatedMealTool
import com.health.friday.nutrition.GetTodayNutritionTool
import com.health.friday.nutrition.GetWeekNutritionTool
import com.health.friday.nutrition.LocalNutritionProvider
import com.health.friday.nutrition.CorrectMealTool
import com.health.friday.nutrition.LogMealTool
import com.health.friday.nutrition.calibration.FoodCalibrationEngine
import com.health.friday.nutrition.LogWaterTool
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.WaterRepository
import com.health.friday.reminders.ReminderRepository
import com.health.friday.reminders.ReminderScheduler
import com.health.friday.reminders.ReminderTool
import com.health.friday.settings.SettingsRepository
import com.health.friday.tasks.GoalRepository
import com.health.friday.tasks.GoalTool
import com.health.friday.tasks.TodoRepository
import com.health.friday.tasks.TodoTool
import com.health.friday.tools.GetDayTool
import com.health.friday.tools.QueryLifeTool
import com.health.friday.domain.SignalsBuilder
import com.health.friday.loom.LoomDayBuilder
import com.health.friday.proactive.NudgePolicy
import com.health.friday.protocols.ProtocolEngine
import com.health.friday.agentic.FocusModeTool
import com.health.friday.agentic.SystemController
import com.health.friday.ai.tools.SpotifyAiTool
import com.health.friday.ai.tools.WebSearchTool
import com.health.friday.bridge.LocalSyncServer
import com.health.friday.device.ThermalGuard
import com.health.friday.memory.graph.GraphManager
import com.health.friday.memory.graph.MemoryRetriever
import com.health.friday.memory.rag.FridayEmbedder
import com.health.friday.memory.rag.SemanticSearchTool
import com.health.friday.predictive.PredictiveEngine
import com.health.friday.predictive.EnergyForecastTool
import com.health.friday.predictive.PredictProductivityTool
import com.health.friday.stats.CorrelationTool
import com.health.friday.telemetry.AnomalyDetector
import com.health.friday.voice.FridaySpeechRecognizer
import com.health.friday.voice.FridayTtsEngine
import com.health.friday.voice.pipeline.AmbientAudioPipeline
import com.health.friday.voice.pipeline.LiveVoiceSessionEngine
import com.health.friday.voice.pipeline.PcmJournalAudioRecorder
import com.health.friday.voice.pipeline.SileroVadEngine
import com.health.friday.voice.pipeline.StreamingAsrEngine
import com.health.friday.voice.pipeline.WakewordEngine
import com.health.friday.bridge.DeviceIdentity
import com.health.friday.sync.cloud.AndroidHybridLogicalClock
import com.health.friday.spotify.SpotifyAuth

class AppContainer(private val context: Context) {

    val database by lazy { FridayDatabase.getDatabase(context) }
    val deviceIdentity by lazy { DeviceIdentity(context) }
    val cloudSyncClock by lazy {
        AndroidHybridLogicalClock(
            database = database,
            dao = database.cloudSyncDao(),
            nodeId = deviceIdentity.deviceId
        )
    }
    val settingsRepository by lazy { SettingsRepository(context) }
    val healthConnectRepository by lazy { HealthConnectRepository(context) }
    val usageRepository by lazy { DeviceUsageRepository(context) }
    val speechRecognizer by lazy { FridaySpeechRecognizer(context) }
    val ttsEngine by lazy { FridayTtsEngine(context) }
    val spotifyAuth by lazy { SpotifyAuth(context) }

    val vadEngine by lazy { SileroVadEngine(context) }
    val wakewordEngine by lazy { WakewordEngine(context) }
    val streamingAsrEngine by lazy { StreamingAsrEngine(context, speechRecognizer) }
    val ambientAudioPipeline by lazy { AmbientAudioPipeline(vadEngine, wakewordEngine, streamingAsrEngine) }
    val liveVoiceSessionEngine by lazy { LiveVoiceSessionEngine(context, vadEngine, speechRecognizer) }
    val pcmJournalAudioRecorder by lazy { PcmJournalAudioRecorder(context) }

    val nutritionProvider by lazy { LocalNutritionProvider(context) }
    val foodCalibrationEngine by lazy { FoodCalibrationEngine(database.foodCalibrationDao()) }
    val nutritionRepository by lazy { NutritionRepository(database.mealDao(), nutritionProvider, healthConnectRepository, foodCalibrationEngine) }
    val waterRepository by lazy { WaterRepository(database.waterDao(), healthConnectRepository) }
    val todoRepository by lazy { TodoRepository(database.todoDao()) }
    val goalRepository by lazy { GoalRepository(database.goalDao()) }
    val embedder by lazy { FridayEmbedder(context) }
    val journalRepository by lazy { JournalRepository(database.journalDao(), embedder) }

    val chatRepository by lazy { ChatRepository(database.chatConversationDao(), database.chatMessageDao()) }
    val reminderScheduler by lazy { ReminderScheduler(context) }
    val reminderRepository by lazy { ReminderRepository(database.reminderDao(), reminderScheduler) }
    val alarmScheduler by lazy { AlarmScheduler(context) }
    val alarmRepository by lazy { AlarmRepository(database.alarmDao(), alarmScheduler) }

    val signalsDao by lazy { database.dailySignalsDao() }
    val proactiveDao by lazy { database.proactiveDao() }
    val memoryFactDao by lazy { database.memoryFactDao() }
    val actionLogDao by lazy { database.actionLogDao() }
    val actionRecorder by lazy { ActionRecorder(actionLogDao) }

    val signalsBuilder by lazy {
        SignalsBuilder(signalsDao, database.mealDao(), database.waterDao(), database.todoDao(), healthConnectRepository, usageRepository)
    }

    val nudgePolicy by lazy { NudgePolicy(proactiveDao) }
    val loomDayBuilder by lazy {
        LoomDayBuilder(database.mealDao(), database.waterDao(), database.todoDao(), database.journalDao(), signalsDao, signalsBuilder)
    }
    val protocolEngine by lazy { ProtocolEngine(context, proactiveDao, signalsDao, aiToolRegistry) }
    val anomalyDetector by lazy { AnomalyDetector(context, signalsDao, nudgePolicy) }
    val predictiveEngine by lazy { PredictiveEngine(signalsDao, database.mealDao()) }
    val systemController by lazy { SystemController(context) }

    val aiToolRegistry by lazy {
        AiToolRegistry(
            tools = listOf(
                LogMealTool(nutritionRepository, actionRecorder),
                LogEstimatedMealTool(nutritionRepository, actionRecorder),
                LogWaterTool(waterRepository, actionRecorder),
                UndoLastActionTool(actionLogDao, nutritionRepository, waterRepository),
                CorrectMealTool(nutritionRepository, actionRecorder),
                GetTodayNutritionTool(nutritionRepository, waterRepository),
                GetWeekNutritionTool(nutritionRepository, waterRepository),
                TodoTool(todoRepository),
                GoalTool(goalRepository),
                ReminderTool(reminderRepository),
                AlarmTool(alarmRepository),
                GetDayTool(signalsDao, signalsBuilder),
                QueryLifeTool(signalsDao, signalsBuilder),
                SemanticSearchTool(database.journalDao(), embedder),
                MemoryTool(database.memoryFactDao(), graphManager),
                FocusModeTool(systemController),
                EnergyForecastTool(predictiveEngine),
                PredictProductivityTool(predictiveEngine, signalsDao),
                CorrelationTool(signalsDao),
                SpotifyAiTool(context),
                WebSearchTool()
            )
        )
    }

    val thermalGuard by lazy { ThermalGuard(context) }
    val localLlmClient: LocalLlmClient by lazy { LocalLlmClient(context, aiToolRegistry) }
    val geminiClient: GeminiAiClient by lazy { GeminiAiClient(settingsRepository, aiToolRegistry) }

    val aiClient: AiClient by lazy {
        RoutingAiClient(settingsRepository, localLlmClient, geminiClient, thermalGuard)
    }

    val memoryRetriever: MemoryRetriever by lazy { MemoryRetriever(embedder) }
    val graphManager: GraphManager by lazy { GraphManager(memoryFactDao, embedder, memoryRetriever) { aiClient } }

    val syncServer by lazy { LocalSyncServer(context) }

    val aiOrchestrator: AiOrchestrator by lazy {
        AiOrchestrator(
            aiClient = aiClient,
            toolRegistry = aiToolRegistry,
            memoryFactDao = memoryFactDao,
            actionRecorder = actionRecorder,
            signalsBuilder = signalsBuilder,
            signalsDao = signalsDao,
            chatRepository = chatRepository,
            graphManager = graphManager,
            embedder = embedder
        )
    }
}
