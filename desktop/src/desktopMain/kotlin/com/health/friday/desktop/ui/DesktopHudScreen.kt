package com.health.friday.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.health.friday.desktop.features.debug.SkiaDebugOverlay
import com.health.friday.desktop.features.hud.HudIntent
import com.health.friday.desktop.features.hud.HudScreenModel
import com.health.friday.desktop.features.sensory.SensoryEngine
import com.health.friday.desktop.ui.components.*
import com.health.friday.desktop.ui.screens.*
import com.health.friday.desktop.ui.spatial.glassAssembleEntrance
import com.health.friday.desktop.ui.spatial.spatialParallax
import com.health.friday.desktop.ui.spatial.spatialZTransition
import com.health.friday.shared.models.JournalEntryDto
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

class DesktopHudScreen : Screen {
    
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { GlobalContext.get().get<HudScreenModel>() }
        val state by screenModel.state.collectAsState()
        val sensoryEngine = remember { runCatching { GlobalContext.get().get<SensoryEngine>() }.getOrNull() }

        var isDashboardReady by remember { mutableStateOf(false) }
        var isDebugOverlayEnabled by remember { mutableStateOf(false) }

        // Assembly Timeline Sequence
        LaunchedEffect(Unit) {
            delay(300)
            sensoryEngine?.playCommandOpen()
            isDashboardReady = true
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
            GlassCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .glassAssembleEntrance(isVisible = isDashboardReady)
                    .spatialParallax(maxTiltDegrees = 1.125f)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(260.dp)
                            .padding(12.dp)
                            .reactiveGlow(
                                isActive = state.isPhoneConnected,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        DesktopSidebar(
                            selectedTab = state.selectedTab,
                            isPhoneConnected = state.isPhoneConnected,
                            onTabSelected = { screenModel.processIntent(HudIntent.ChangeTab(it)) }
                        )
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                        if (state.notice != null) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0x667C2D12))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(state.notice!!, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { screenModel.processIntent(HudIntent.DismissNotice) }) {
                                        Text("Dismiss", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        DesktopHeader(
                            selectedTabTitle = state.selectedTab.title,
                            personaStyle = state.personaStyle,
                            activeModel = state.activeModel,
                            onAddWater = { screenModel.processIntent(HudIntent.AddWater(it)) },
                            onOpenAssistant = { screenModel.processIntent(HudIntent.ChangeTab(DesktopNavTab.ASSISTANT)) },
                            onToggleHudMode = { screenModel.processIntent(HudIntent.ToggleHudMode) }
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = state.selectedTab,
                                transitionSpec = { spatialZTransition() },
                                label = "tab_transition"
                            ) { tab ->
                                when (tab) {
                                    DesktopNavTab.HOME -> DesktopHomeScreen(state.dailySignals, state.meals, state.tasks) {
                                        screenModel.processIntent(HudIntent.ToggleTask(it))
                                    }
                                    DesktopNavTab.LOOM -> DesktopLoomScreen(state.dailySignals, state.meals)
                                    DesktopNavTab.HEALTH -> DesktopHealthScreen(state.dailySignals, state.isPhoneConnected)
                                    DesktopNavTab.NUTRITION -> DesktopNutritionScreen(state.meals) { name, cal, p, c, f ->
                                        screenModel.processIntent(HudIntent.LogMeal(name, cal, p, c, f))
                                    }
                                    DesktopNavTab.TASKS -> DesktopTasksScreen(
                                        state.tasks, state.reminders, state.dailySignals,
                                        goals = state.goals,
                                        onAddTask = { screenModel.processIntent(HudIntent.AddTask(it)) },
                                        onToggleTask = { screenModel.processIntent(HudIntent.ToggleTask(it)) },
                                        onToggleGoal = { screenModel.processIntent(HudIntent.ToggleGoal(it)) },
                                        onAddReminder = { t, ms -> screenModel.processIntent(HudIntent.AddReminder(t, ms)) }
                                    )
                                    DesktopNavTab.JOURNAL -> DesktopJournalScreen(
                                        entries = state.journalEntries.map { entry ->
                                            JournalEntryDto(
                                                id = entry.syncId,
                                                content = entry.text,
                                                timestamp = entry.createdAt
                                            )
                                        },
                                        onAddJournalEntry = {
                                            screenModel.processIntent(HudIntent.AddJournalEntry(it))
                                        }
                                    )
                                    DesktopNavTab.ASSISTANT -> DesktopAssistantScreen(
                                        state.messages, state.sessions, state.currentSessionId,
                                        onSendMessage = { screenModel.processIntent(HudIntent.SendMessage(it)) },
                                        onNewChat = { screenModel.processIntent(HudIntent.NewChat) },
                                        onSelectSession = { screenModel.processIntent(HudIntent.SelectSession(it)) },
                                        onDeleteSession = { screenModel.processIntent(HudIntent.DeleteSession(it)) },
                                        onClearHistory = { screenModel.processIntent(HudIntent.ClearHistory) }
                                    )
                                    DesktopNavTab.DIAGNOSTICS -> DesktopDiagnosticsScreen(state.isPhoneConnected, state.activeModel, state.phoneName)
                                    DesktopNavTab.SETTINGS -> DesktopSettingsScreen(
                                        apiKey = state.apiKey,
                                        personaStyle = state.personaStyle,
                                        selectedLocalModel = state.activeModel,
                                        onUpdateSettings = { key, persona, model ->
                                            screenModel.processIntent(HudIntent.UpdateSettings(key, persona, model))
                                        },
                                        onDriveConnect = { screenModel.connectGoogleDrive(it) },
                                        onDriveBackup = { screenModel.backUpGoogleDriveRecoveryKey(it) },
                                        onDriveRestore = { screenModel.restoreGoogleDriveRecoveryKey(it) },
                                        onDriveSync = { screenModel.syncGoogleDrive() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skia Telemetry Debug Overlay
            SkiaDebugOverlay(isEnabled = isDebugOverlayEnabled)
        }
    }
}
