package com.health.friday.desktop.features.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WidgetType {
    HEALTH_VITALS,
    TASK_TRACKER,
    NUTRITION_LOG,
    MINI_ASSISTANT,
    TELEMETRY_SPARKLINE
}

// Represents the state, position, and sizing of a widget on the grid
data class WidgetConfig(
    val id: String,
    val type: WidgetType,
    val columnSpan: Int = 1, // How many grid columns it consumes
    val defaultHeight: Dp = 200.dp
)
