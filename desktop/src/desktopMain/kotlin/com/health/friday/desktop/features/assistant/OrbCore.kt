package com.health.friday.desktop.features.assistant

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class OrbEmotion {
    IDLE,       // Slow, organic breathing
    LISTENING,  // Expanding rings, highly receptive
    THINKING,   // Rapid inward spiral, processing
    SPEAKING,   // Sharp, high-amplitude spikes synced to TTS
    ALERT       // Red/Orange, sharp asymmetric spikes
}

data class OrbContext(
    val emotion: OrbEmotion = OrbEmotion.IDLE,
    val amplitude: Float = 0f,
    val lookVector: Offset = Offset.Zero // X, Y offset pointing toward the mouse
)

// Modifier to track mouse proximity and calculate the "look" vector
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trackProximity(
    orbCenter: Offset,
    onVectorCalculated: (Offset) -> Unit
): Modifier = this.onPointerEvent(PointerEventType.Move) { event ->
    val pointerPos = event.changes.first().position
    val dx = pointerPos.x - orbCenter.x
    val dy = pointerPos.y - orbCenter.y

    // Calculate the angle and limit the physical "eye" movement radius
    val angle = atan2(dy, dx)
    val distance = minOf(Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(), 20f) // Max 20px lean

    val lookOffset = Offset(
        x = cos(angle) * distance,
        y = sin(angle) * distance
    )
    onVectorCalculated(lookOffset)
}
