package com.health.friday.desktop.features.command

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.features.sensory.SensoryEngine
import com.health.friday.desktop.ui.components.GlassCard
import com.health.friday.desktop.ui.components.reactiveGlow
import com.health.friday.desktop.ui.spatial.glassAssembleEntrance
import org.koin.core.context.GlobalContext

@Composable
fun CommandSurfaceOverlay(
    isVisible: Boolean,
    onClose: () -> Unit,
    onActionSelected: (String) -> Unit
) {
    val engine = remember { GlobalContext.get().get<CommandSurfaceEngine>() }
    val sensoryEngine = remember { runCatching { GlobalContext.get().get<SensoryEngine>() }.getOrNull() }
    val suggestions by engine.contextualSuggestions.collectAsState(emptyList())
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sensoryEngine?.playCommandOpen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isVisible) Color(0x66000000) else Color.Transparent) // Dims the rest of the OS
            .clickable { onClose() }
            .padding(top = 150.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // The Glass Panel that snaps into place
        GlassCard(
            modifier = Modifier
                .width(700.dp)
                .clickable(enabled = false) {}
                .glassAssembleEntrance(isVisible)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {

                // 1. The Unified Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 28.sp),
                        cursorBrush = SolidColor(Color(0xFF00E5FF)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text("Ask FRIDAY or type a command...", color = Color.White.copy(alpha = 0.3f), fontSize = 28.sp)
                            }
                            innerTextField()
                        }
                    )

                    // Voice Input Trigger (Pulses when active)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .reactiveGlow(isActive = false, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Command", tint = Color(0xFF00E5FF))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. The Contextual Quick Actions (Chips)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    suggestions.forEach { action ->
                        val chipColor = if (action.isPrimary) Color(0x4D00E5FF) else Color(0x1AFFFFFF)
                        val textColor = if (action.isPrimary) Color(0xFF00E5FF) else Color.White

                        Box(
                            modifier = Modifier
                                .background(chipColor, RoundedCornerShape(8.dp))
                                .clickable { onActionSelected(action.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(action.title, color = textColor, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
