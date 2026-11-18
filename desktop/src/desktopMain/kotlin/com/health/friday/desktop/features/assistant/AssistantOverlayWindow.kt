package com.health.friday.desktop.features.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.health.friday.desktop.features.sensory.SensoryEngine
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AssistantOverlayWindow(onClose: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var lookVector by remember { mutableStateOf(Offset.Zero) }

    val sensoryEngine = remember { runCatching { GlobalContext.get().get<SensoryEngine>() }.getOrNull() }
    val windowInfo = LocalWindowInfo.current

    // Simulate AI State for now
    val orbContext = OrbContext(
        emotion = if (isExpanded) OrbEmotion.LISTENING else OrbEmotion.IDLE,
        amplitude = 0.5f,
        lookVector = lookVector
    )

    val windowState = rememberWindowState(
        width = if (isExpanded) 400.dp else 140.dp,
        height = if (isExpanded) 600.dp else 140.dp,
        position = WindowPosition(Alignment.BottomEnd)
    )

    LaunchedEffect(orbContext.emotion, windowState.position.x) {
        if (orbContext.emotion == OrbEmotion.THINKING) {
            val screenWidth = windowInfo.containerSize.width.toFloat()
            val currentX = windowState.position.x.value
            sensoryEngine?.playOrbProcessing(
                orbScreenPositionX = currentX,
                screenWidth = screenWidth
            )
        }
    }

    Window(
        onCloseRequest = onClose,
        state = windowState,
        transparent = true,
        undecorated = true,
        alwaysOnTop = true
    ) {
        // Container that gracefully animates its size when isExpanded changes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(if (isExpanded) 16.dp else 100.dp))
                .background(if (isExpanded) Color(0xCC0F172A) else Color.Transparent)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
                .trackProximity(Offset(70f, 70f)) { vector ->
                    if (!isExpanded) lookVector = vector
                }
        ) {
            if (!isExpanded) {
                // 1. Compact Mode: Just the reactive orb
                ExpressiveOrbRenderer(
                    context = orbContext,
                    modifier = Modifier.fillMaxSize().clickable { isExpanded = true }
                )
            } else {
                // 2. Expanded Mode: The orb shifts to the top, chat interface reveals below
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveOrbRenderer(
                            context = orbContext.copy(lookVector = Offset.Zero),
                            modifier = Modifier.size(64.dp) // Miniature orb acts as the header icon
                        )
                        // Close button to collapse back to Orb mode
                        IconButton(onClick = { isExpanded = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Collapse",
                                tint = Color.White
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        /* Assistant Chat UI */
                    }
                }
            }
        }
    }
}
