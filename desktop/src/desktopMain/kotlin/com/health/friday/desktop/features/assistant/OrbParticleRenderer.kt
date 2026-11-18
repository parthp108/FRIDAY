package com.health.friday.desktop.features.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

@Composable
fun OrbParticleRenderer(voiceAmplitude: Float = 0.5f, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val phaseShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
    )

    val baseColor = Color(0xFF00E5FF) // Cyan Core

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.minDimension / 3

        drawCircle(
            brush = Brush.radialGradient(listOf(baseColor, Color.Transparent), center, baseRadius * 1.5f),
            radius = baseRadius,
            center = center,
            blendMode = BlendMode.Screen
        )

        for (i in 1..4) {
            val distortion = sin(phaseShift + i) * (voiceAmplitude * 30f)
            drawCircle(
                color = baseColor.copy(alpha = 0.5f / i),
                radius = baseRadius + (i * 10f) + distortion,
                center = center,
                style = Stroke(width = 2f)
            )
        }
    }
}
