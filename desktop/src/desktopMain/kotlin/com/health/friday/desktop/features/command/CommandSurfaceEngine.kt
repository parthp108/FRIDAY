package com.health.friday.desktop.features.command

import com.health.friday.desktop.features.intelligence.ContextEngine
import com.health.friday.desktop.features.intelligence.PriorityLevel
import com.health.friday.desktop.features.widgets.WidgetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CommandAction(
    val id: String,
    val title: String,
    val icon: String, // E.g., Material Icon reference
    val isPrimary: Boolean = false
)

class CommandSurfaceEngine(private val contextEngine: ContextEngine) {

    // Dynamically generates chips based on FRIDAY's active intelligence state
    val contextualSuggestions: Flow<List<CommandAction>> = contextEngine.widgetLayoutState.map { layout ->
        val suggestions = mutableListOf<CommandAction>()

        // 1. Health Context
        val healthState = layout[WidgetType.HEALTH_VITALS]
        if (healthState?.priority == PriorityLevel.CRITICAL) {
            suggestions.add(CommandAction("log_symptoms", "Log Symptoms (High HR)", "Favorite", true))
            suggestions.add(CommandAction("start_breathing", "Start 2m Breathing Sync", "Air", true))
        }

        // 2. Task Context
        val taskState = layout[WidgetType.TASK_TRACKER]
        if ((taskState?.priority?.weight ?: 0) >= 2) {
            suggestions.add(CommandAction("focus_mode", "Enter Deep Focus", "Timer"))
        }

        // 3. Default / Fallback Actions
        suggestions.add(CommandAction("new_chat", "Ask Assistant", "Chat"))
        suggestions.add(CommandAction("turn_off_screen", "Sleep Displays", "Display"))

        suggestions
    }
}
