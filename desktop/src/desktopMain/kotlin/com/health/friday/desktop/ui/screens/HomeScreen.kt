package com.health.friday.desktop.ui.screens

import androidx.compose.runtime.*
import com.health.friday.desktop.features.intelligence.ContextEngine
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayMealDto
import com.health.friday.shared.models.FridayTaskDto
import org.koin.core.context.GlobalContext

@Composable
fun DesktopHomeScreen(
    signals: DailySignalDto,
    meals: List<FridayMealDto>,
    tasks: List<FridayTaskDto>,
    onToggleTask: (FridayTaskDto) -> Unit
) {
    val contextEngine = remember { GlobalContext.get().get<ContextEngine>() }

    LaunchedEffect(signals, tasks) {
        contextEngine.updateTelemetry(signals, tasks)
    }

    AdaptiveDashboardGrid(
        signals = signals,
        meals = meals,
        tasks = tasks,
        onToggleTask = onToggleTask
    )
}
