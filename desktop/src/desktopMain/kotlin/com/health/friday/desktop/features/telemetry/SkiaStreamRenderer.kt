package com.health.friday.desktop.features.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun SkiaStreamRenderer(
    rawPixelsFlow: StateFlow<ByteArray?>,
    modifier: Modifier = Modifier
) {
    // Collect the flow as state. This triggers a change, but we isolate it below.
    val currentFrameBytes by rawPixelsFlow.collectAsState()

    // derivedStateOf ensures we only attempt to decode a new bitmap when the bytes ACTUALLY change,
    // and it prevents the parent composable from constantly redrawing.
    val currentBitmap = remember {
        derivedStateOf {
            val bytes = currentFrameBytes ?: return@derivedStateOf null
            try {
                // Skia native decoding bypasses standard heavy JVM image processing
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val frame = currentBitmap.value

        if (frame != null) {
            // Draw directly to the low-level Skia canvas
            drawImage(
                image = frame,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        } else {
            // Fallback visualization if the frame drops
            drawRect(color = Color(0xFF1E2832), size = size)
        }
    }
}
