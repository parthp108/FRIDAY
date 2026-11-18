package com.health.friday.desktop.features.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun OptimizedStreamSurface(rawPixelsState: State<ByteArray?>) {
    // derivedStateOf prevents the parent Composable from recomposing.
    // It only recalculates the ImageBitmap when the underlying ByteArray changes.
    val currentFrame = remember {
        derivedStateOf {
            val pixels = rawPixelsState.value ?: return@derivedStateOf null
            try {
                SkiaImage.makeFromEncoded(pixels).toComposeImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frame = currentFrame.value
        if (frame != null) {
            // Draw natively to the Canvas bypassing the standard Image() composable overhead
            drawImage(
                image = frame,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}
