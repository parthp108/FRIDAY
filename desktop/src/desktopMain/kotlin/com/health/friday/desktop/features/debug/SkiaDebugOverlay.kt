package com.health.friday.desktop.features.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.health.friday.desktop.features.intelligence.ContextEngine
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.koin.core.context.GlobalContext

@Composable
fun SkiaDebugOverlay(isEnabled: Boolean) {
    if (!isEnabled) return

    val contextEngine = remember { runCatching { GlobalContext.get().get<ContextEngine>() }.getOrNull() }
    val layoutMap by contextEngine?.widgetLayoutState?.collectAsState(emptyMap()) ?: remember { mutableStateOf(emptyMap()) }

    var currentFps by remember { mutableStateOf(0) }
    var frameCount by remember { mutableStateOf(0) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Manually track FPS outside the Compose recomposition cycle
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {
                frameCount++
                val now = System.currentTimeMillis()
                if (now - lastTime >= 1000) {
                    currentFps = frameCount
                    frameCount = 0
                    lastTime = now
                }
            }
        }
    }

    // Bypass Compose Text and draw directly via Skia for zero overhead
    Canvas(modifier = Modifier.fillMaxSize()) {
        val nativeCanvas = drawContext.canvas.nativeCanvas

        val paint = Paint().apply { color = Color.makeARGB(255, 0, 255, 0) } // Hacker Green
        val font = Font(null, 14f)

        var yOffset = 40f
        fun drawDebugLine(text: String) {
            val textLine = TextLine.make(text, font)
            nativeCanvas.drawTextLine(textLine, 20f, yOffset, paint)
            yOffset += 20f
        }

        drawDebugLine("=== FRIDAY ECo DEBUG ===")
        drawDebugLine("RENDER FPS : $currentFps / 60")
        drawDebugLine("JVM MEMORY : ${Runtime.getRuntime().totalMemory() / (1024 * 1024)} MB")
        drawDebugLine(" ")
        drawDebugLine("--- CONTEXT ENGINE PIPELINE ---")

        layoutMap.forEach { (type, state) ->
            drawDebugLine("[${type.name}] Mode: ${state.mode.name} | Prio: ${state.priority.weight} | Note: ${state.overrideReason ?: "OK"}")
        }
    }
}
