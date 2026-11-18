package com.health.friday.desktop.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.features.intelligence.ContextEngine
import com.health.friday.desktop.features.intelligence.WidgetIntelligenceState
import com.health.friday.desktop.features.intelligence.WidgetMode
import com.health.friday.desktop.features.layout.AdaptiveLayoutState
import com.health.friday.desktop.features.widgets.*
import com.health.friday.desktop.network.TelemetryClient
import com.health.friday.desktop.repository.todayMeals
import com.health.friday.desktop.ui.components.WidgetContainer
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayMealDto
import com.health.friday.shared.models.FridayTaskDto
import org.koin.core.context.GlobalContext

@Composable
fun AdaptiveDashboardGrid(
    signals: DailySignalDto,
    meals: List<FridayMealDto>,
    tasks: List<FridayTaskDto>,
    onToggleTask: (FridayTaskDto) -> Unit
) {
    val contextEngine = remember { GlobalContext.get().get<ContextEngine>() }
    val layoutState = remember { GlobalContext.get().get<AdaptiveLayoutState>() }

    val intelligenceMap by contextEngine.widgetLayoutState.collectAsState(initial = emptyMap())
    val pinnedWidgets by layoutState.pinnedWidgets.collectAsState()
    val isFocusMode by layoutState.isFocusMode.collectAsState()

    val totalCalories = meals.todayMeals().sumOf { it.calories }
    val waterMl = signals.waterIntakeMl
    val dummyClient = remember { TelemetryClient("127.0.0.1") }

    // Calculate the final rendering list
    val sortedWidgets = remember(intelligenceMap, pinnedWidgets, isFocusMode) {
        val visibleWidgets = WidgetType.entries.mapNotNull { type ->
            val state = intelligenceMap[type] ?: return@mapNotNull null
            if (state.mode == WidgetMode.HIDDEN) null else Pair(type, state)
        }

        if (isFocusMode) {
            // FOCUS MODE: Only show pinned widgets, or the single highest priority widget
            val focusTarget = visibleWidgets.filter { it.first in pinnedWidgets }.ifEmpty {
                listOfNotNull(visibleWidgets.maxByOrNull { it.second.priority.weight })
            }
            focusTarget
        } else {
            // STANDARD MODE: Sort by Pinned First, then Priority Score
            visibleWidgets.sortedWith(
                compareByDescending<Pair<WidgetType, WidgetIntelligenceState>> {
                    it.first in pinnedWidgets
                }.thenByDescending {
                    it.second.priority.weight
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFocusMode) "Deep Focus Surface" else "Self-Arranging Intelligence Grid",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            IconButton(onClick = { layoutState.toggleFocusMode() }) {
                Icon(
                    imageVector = if (isFocusMode) Icons.Filled.CenterFocusStrong else Icons.Outlined.CenterFocusWeak,
                    contentDescription = "Toggle Focus Mode",
                    tint = if (isFocusMode) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f)
                )
            }
        }

        LazyVerticalStaggeredGrid(
            columns = if (isFocusMode) StaggeredGridCells.Fixed(1) else StaggeredGridCells.Adaptive(minSize = 350.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp
        ) {
            items(sortedWidgets, key = { it.first.name }) { (type, intelligence) ->
                val targetHeight = when {
                    isFocusMode -> 550.dp
                    type in pinnedWidgets -> 350.dp
                    intelligence.mode == WidgetMode.MINIMAL -> 120.dp
                    intelligence.mode == WidgetMode.EXPANDED -> 250.dp
                    intelligence.mode == WidgetMode.IMMERSIVE -> 400.dp
                    else -> 200.dp
                }

                val itemModifier = Modifier
                    .fillMaxWidth()
                    .height(targetHeight)
                    .animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                val config = WidgetConfig(type.name, type, defaultHeight = targetHeight)

                when (type) {
                    WidgetType.HEALTH_VITALS -> IntelligentHealthWidget(signals, intelligence, config, itemModifier)
                    WidgetType.TELEMETRY_SPARKLINE -> ZeroLatencyStreamWidget(dummyClient, isConnected = false, modifier = itemModifier)
                    WidgetType.NUTRITION_LOG -> {
                        WidgetContainer(title = "Nutrition & Hydration", config = config, widgetType = type, modifier = itemModifier) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Text("Today Calories: $totalCalories kcal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Water: ${waterMl?.let { "$it ml" } ?: "Goal 2,500 ml"}", color = Color(0xFF00E5FF), fontSize = 14.sp)
                            }
                        }
                    }
                    WidgetType.TASK_TRACKER -> TaskTrackerWidget(tasks = tasks, onToggle = onToggleTask, config = config, modifier = itemModifier)
                    else -> {
                        WidgetContainer(title = type.name.replace("_", " "), config = config, widgetType = type, modifier = itemModifier) {
                            Text("Widget: ${type.name}", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
