package com.health.friday.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

// The core states of FRIDAY's consciousness
enum class OrbState {
    IDLE, LISTENING, THINKING, SPEAKING
}

val FridayCyanColor = Color(0xFF00E5FF)
val FridayOrangeColor = Color(0xFFF59E0B)
val FridayRedColor = Color(0xFFEF4444)

@Composable
fun FridayOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "friday_orb_transition")

    // Slow, smooth rotation for outer tech ring
    val rotationCw by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_cw"
    )

    // Counter-clockwise rotation for inner ring
    val rotationCcw by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_ccw"
    )

    // Smooth breathing alpha
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val audioReactiveLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(90, easing = LinearEasing),
        label = "audio_level_reactive"
    )

    val activeAccentColor = when (state) {
        OrbState.LISTENING -> FridayRedColor
        OrbState.THINKING -> FridayOrangeColor
        OrbState.SPEAKING -> FridayCyanColor
        OrbState.IDLE -> FridayCyanColor
    }

    Canvas(modifier = modifier.size(120.dp)) {
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f * 0.85f

        // 1. Core Glowing Aura
        val coreRadius = maxRadius * (if (state == OrbState.LISTENING) 0.55f + audioReactiveLevel * 0.45f else 0.5f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    activeAccentColor.copy(alpha = pulseAlpha * 0.45f),
                    Color.Transparent
                ),
                center = centerOffset,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = centerOffset
        )

        // 2. Minimalist Outer Ring (Dashed, Sleek)
        rotate(degrees = rotationCcw, pivot = centerOffset) {
            drawCircle(
                color = FridayCyanColor.copy(alpha = 0.5f),
                radius = maxRadius,
                center = centerOffset,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(25f, 15f),
                        phase = 0f
                    )
                )
            )
        }

        // 3. Rotating Inner Arc Segment
        val speedMultiplier = when (state) {
            OrbState.THINKING -> 2.5f
            OrbState.SPEAKING -> 1.8f
            else -> 1f
        }
        rotate(degrees = rotationCw * speedMultiplier, pivot = centerOffset) {
            val arcSize = Size(maxRadius * 1.45f, maxRadius * 1.45f)
            val arcTopLeft = Offset(centerOffset.x - arcSize.width / 2f, centerOffset.y - arcSize.height / 2f)

            drawArc(
                color = activeAccentColor,
                startAngle = 0f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // 4. Center Precision Reticle
        val reticleRadius = maxRadius * 0.32f
        drawCircle(
            color = activeAccentColor.copy(alpha = pulseAlpha),
            radius = reticleRadius,
            center = centerOffset,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Precision Crosshair Ticks
        val lineLen = 6.dp.toPx()
        val lineOffset = reticleRadius + 4.dp.toPx()

        drawLine(
            color = activeAccentColor,
            start = Offset(centerOffset.x, centerOffset.y - lineOffset),
            end = Offset(centerOffset.x, centerOffset.y - lineOffset - lineLen),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = activeAccentColor,
            start = Offset(centerOffset.x, centerOffset.y + lineOffset),
            end = Offset(centerOffset.x, centerOffset.y + lineOffset + lineLen),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = activeAccentColor,
            start = Offset(centerOffset.x - lineOffset, centerOffset.y),
            end = Offset(centerOffset.x - lineOffset - lineLen, centerOffset.y),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = activeAccentColor,
            start = Offset(centerOffset.x + lineOffset, centerOffset.y),
            end = Offset(centerOffset.x + lineOffset + lineLen, centerOffset.y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}
