package com.health.friday.desktop.features.intelligence

import com.health.friday.desktop.features.widgets.WidgetType
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayTaskDto
import kotlinx.coroutines.flow.*

class ContextEngine {

    private val _healthSignals = MutableStateFlow(DailySignalDto(sleepScore = 85, waterIntakeMl = 1500, activeMinutes = 35))
    private val _activeTasks = MutableStateFlow<List<FridayTaskDto>>(emptyList())

    fun updateTelemetry(signals: DailySignalDto, tasks: List<FridayTaskDto>) {
        _healthSignals.value = signals
        _activeTasks.value = tasks
    }

    /**
     * The master brain. Combines all incoming signals and outputs a real-time layout map.
     * The UI simply collects this Flow and magically rearranges itself.
     */
    val widgetLayoutState: Flow<Map<WidgetType, WidgetIntelligenceState>> = combine(
        _healthSignals, _activeTasks
    ) { signals, tasks ->

        val layoutMap = mutableMapOf<WidgetType, WidgetIntelligenceState>()

        // 1. Evaluate Health Vitals Widget
        val activeMins = signals.activeMinutes ?: 0
        val sleep = signals.sleepScore
        layoutMap[WidgetType.HEALTH_VITALS] = when {
            activeMins > 60 -> {
                WidgetIntelligenceState(WidgetMode.IMMERSIVE, PriorityLevel.CRITICAL, "High Active Duration")
            }
            sleep != null && sleep < 50 -> {
                WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.ELEVATED, "Low Sleep Recovery")
            }
            else -> {
                WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.STANDARD)
            }
        }

        // 2. Evaluate Task Tracker Widget
        val urgentTasks = tasks.count { !it.isCompleted }
        layoutMap[WidgetType.TASK_TRACKER] = when {
            urgentTasks == 0 -> WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.BACKGROUND)
            urgentTasks > 3 -> WidgetIntelligenceState(WidgetMode.IMMERSIVE, PriorityLevel.ELEVATED)
            else -> WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.STANDARD)
        }

        // 3. Mini Assistant & Telemetry Sparkline
        layoutMap[WidgetType.MINI_ASSISTANT] = WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.STANDARD)
        layoutMap[WidgetType.TELEMETRY_SPARKLINE] = WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.BACKGROUND)
        layoutMap[WidgetType.NUTRITION_LOG] = WidgetIntelligenceState(WidgetMode.EXPANDED, PriorityLevel.STANDARD)

        layoutMap
    }
}
