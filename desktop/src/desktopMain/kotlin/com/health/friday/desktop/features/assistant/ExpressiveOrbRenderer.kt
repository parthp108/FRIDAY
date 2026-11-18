package com.health.friday.desktop.features.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ExpressiveOrbRenderer(
    context: OrbContext,
    modifier: Modifier = Modifier
) {
    // 1. Core Physics & Timing
    val transition = rememberInfiniteTransition()
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(if (context.emotion == OrbEmotion.THINKING) 1000 else 4000, easing = LinearEasing))
    )

    // 2. Emotional Color Mapping
    val targetColor = when (context.emotion) {
        OrbEmotion.IDLE -> Color(0x6600E5FF)
        OrbEmotion.LISTENING -> Color(0xFF00E5FF)
        OrbEmotion.THINKING -> Color(0xFFB388FF) // Deep Purple for processing
        OrbEmotion.SPEAKING -> Color(0xFF00E5FF)
        OrbEmotion.ALERT -> Color(0xFFFF3B30)
    }
    val orbColor by animateColorAsState(targetColor, tween(500))

    Canvas(modifier = modifier.fillMaxSize()) {
        val baseCenter = Offset(size.width / 2, size.height / 2)
        // Shift the entire rendering center based on mouse proximity
        val center = baseCenter + context.lookVector
        val baseRadius = size.minDimension / 4

        // The Glowing Core
        drawCircle(
            brush = Brush.radialGradient(listOf(orbColor, Color.Transparent), center, baseRadius * 1.5f),
            radius = baseRadius * 1.5f,
            center = center,
            blendMode = BlendMode.Screen
        )

        // Shape Shifting based on state
        when (context.emotion) {
            OrbEmotion.IDLE -> {
                // Gentle Breathing Rings
                val breath = sin(time) * 5f
                for (i in 1..3) {
                    drawCircle(
                        color = orbColor.copy(alpha = 0.5f / i),
                        radius = baseRadius + (i * 15f) + breath,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
            }
            OrbEmotion.THINKING -> {
                // Inward Spiral
                rotate(degrees = (time * 180f / PI.toFloat())) {
                    val spiralPath = Path().apply {
                        for (i in 0..360 step 10) {
                            val rad = (i * PI / 180).toFloat()
                            // Radius shrinks as it spirals inward
                            val r = baseRadius * 2f * (1f - (i / 360f))
                            val x = center.x + cos(rad) * r
                            val y = center.y + sin(rad) * r
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(spiralPath, orbColor, style = Stroke(width = 3f))
                }
            }
            OrbEmotion.SPEAKING, OrbEmotion.LISTENING -> {
                // Audio Reactive Spikes (Starburst)
                val spikePath = Path()
                val points = 64
                for (i in 0..points) {
                    val angle = (i * 2 * PI / points).toFloat()
                    // Random noise scaled by the audio amplitude
                    val noise = (sin(angle * 10 + time * 5) * context.amplitude * 40f)
                    val r = baseRadius + 10f + noise

                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r
                    if (i == 0) spikePath.moveTo(x, y) else spikePath.lineTo(x, y)
                }
                spikePath.close()
                drawPath(spikePath, orbColor, style = Stroke(width = 2f), blendMode = BlendMode.Screen)
            }
            OrbEmotion.ALERT -> {
                // Sharp, jagged math for ALERT state
                val spikePath = Path()
                val points = 32
                for (i in 0..points) {
                    val angle = (i * 2 * PI / points).toFloat()
                    val noise = (if (i % 2 == 0) 25f else -15f) + (sin(angle * 6 + time * 8) * 15f)
                    val r = baseRadius + 15f + noise
                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r
                    if (i == 0) spikePath.moveTo(x, y) else spikePath.lineTo(x, y)
                }
                spikePath.close()
                drawPath(spikePath, orbColor, style = Stroke(width = 2.5f), blendMode = BlendMode.Screen)
            }
        }
    }
}
