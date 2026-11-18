package com.health.friday.desktop.features.telemetry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun SmoothStreamSurface(rawPixels: ByteArray?) {
    // Decode bitmap only when bytes change to prevent Jank
    val bitmap: ImageBitmap? = remember(rawPixels) {
        if (rawPixels == null) return@remember null
        try {
            SkiaImage.makeFromEncoded(rawPixels).toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // Crossfade animation when the stream connects/disconnects
    AnimatedVisibility(
        visible = bitmap != null,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Live Zero-Copy Screen",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
