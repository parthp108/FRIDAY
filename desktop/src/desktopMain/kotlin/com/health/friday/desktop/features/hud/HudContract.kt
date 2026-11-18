package com.health.friday.desktop.features.hud

import com.health.friday.desktop.ui.components.DesktopNavTab
import com.health.friday.shared.models.*

data class HudState(
    val selectedTab: DesktopNavTab = DesktopNavTab.HOME,
    val notice: String? = null,
    val isPhoneConnected: Boolean = false,
    val phoneName: String? = null,
    val activeModel: String = "Local Ollama (llama3.2:1b)",
    val personaStyle: String = "Clean Assistant",
    val apiKey: String = "",
    val messages: List<ChatMessageDto> = emptyList(),
    val sessions: List<ChatSessionDto> = emptyList(),
    val currentSessionId: String = "",
    val tasks: List<FridayTaskDto> = emptyList(),
    val reminders: List<FridayReminderDto> = emptyList(),
    val meals: List<FridayMealDto> = emptyList(),
    val dailySignals: DailySignalDto = DailySignalDto()
)

sealed class HudIntent {
    data class ChangeTab(val tab: DesktopNavTab) : HudIntent()
    object DismissNotice : HudIntent()
    object ToggleHudMode : HudIntent()

    // Feature Actions
    data class SendMessage(val message: String) : HudIntent()
    object NewChat : HudIntent()
    data class SelectSession(val sessionId: String) : HudIntent()
    data class DeleteSession(val sessionId: String) : HudIntent()
    object ClearHistory : HudIntent()

    // Tasks & Health Actions
    data class AddTask(val taskTitle: String) : HudIntent()
    data class ToggleTask(val task: FridayTaskDto) : HudIntent()
    data class AddReminder(val title: String, val timeMs: Long) : HudIntent()
    data class LogMeal(val name: String, val cals: Int, val p: Float, val c: Float, val f: Float) : HudIntent()
    data class AddWater(val amountMl: Int) : HudIntent()
    data class UpdateSettings(val apiKey: String, val persona: String, val localModel: String) : HudIntent()
}
