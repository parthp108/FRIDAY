package com.health.friday.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.health.friday.desktop.theme.AmbientState
import kotlin.math.sin

@Composable
fun AmbientLayer(
    state: AmbientState,
    modifier: Modifier = Modifier
) {
    // Smoothly animate the target colors when the state changes
    val animatedPrimary by animateColorAsState(
        targetValue = state.primaryHue,
        animationSpec = tween(2000, easing = LinearOutSlowInEasing)
    )
    val animatedSecondary by animateColorAsState(
        targetValue = state.secondaryHue,
        animationSpec = tween(2000, easing = LinearOutSlowInEasing)
    )

    // A continuously driving float that controls the "breathing" phase
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_breathing")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(state.pulseSpeedMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate a slight, slow drift for the gradient centers
        val xDrift = sin(phase) * (width * 0.1f)
        val yDrift = sin(phase * 0.5f) * (height * 0.1f)

        // The background is heavily translucent so it blends with the OS wallpaper,
        // but imparts a strong, moving mood.
        val radialBrush = Brush.radialGradient(
            colors = listOf(
                animatedSecondary.copy(alpha = 0.4f), // Brighter core
                animatedPrimary.copy(alpha = 0.7f),   // Darker edge
                Color(0xCC000000)                     // Fades to black at the corners
            ),
            center = Offset((width / 2) + xDrift, (height / 2) + yDrift),
            radius = width.coerceAtLeast(height) * 0.8f
        )

        drawRect(brush = radialBrush)
    }
}
