package com.health.friday.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.features.layout.AdaptiveLayoutState
import com.health.friday.desktop.features.sensory.SensoryEngine
import com.health.friday.desktop.features.widgets.WidgetConfig
import com.health.friday.desktop.features.widgets.WidgetType
import com.health.friday.desktop.features.windows.WindowManager
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WidgetContainer(
    title: String,
    config: WidgetConfig? = null,
    widgetType: WidgetType? = config?.type,
    modifier: Modifier = Modifier,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    content: @Composable BoxScope.() -> Unit
) {
    val layoutState = remember { runCatching { GlobalContext.get().get<AdaptiveLayoutState>() }.getOrNull() }
    val sensoryEngine = remember { runCatching { GlobalContext.get().get<SensoryEngine>() }.getOrNull() }
    val pinnedWidgets by layoutState?.pinnedWidgets?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val effectiveType = widgetType ?: config?.type
    val isPinned = effectiveType != null && pinnedWidgets.contains(effectiveType)
    var isHovered by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Auto-hiding header (appears on hover or when pinned)
            AnimatedVisibility(
                visible = isHovered || isPinned,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag Handle
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag Widget",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            }
                    )

                    Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (effectiveType != null && layoutState != null) {
                            IconButton(
                                onClick = {
                                    sensoryEngine?.playWidgetSnap()
                                    layoutState.togglePin(effectiveType)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = "Pin Widget",
                                    tint = if (isPinned) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (config != null) {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        GlobalContext.get().get<WindowManager>().detachWidget(config)
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Pop out window", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { /* Open Widget Settings */ }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // The actual widget content (Health, Tasks, etc.)
            Box(modifier = Modifier.fillMaxSize().padding(if (isHovered) 4.dp else 16.dp)) {
                content()
            }
        }
    }
}
