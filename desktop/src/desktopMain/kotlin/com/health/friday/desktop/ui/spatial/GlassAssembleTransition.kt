package com.health.friday.desktop.ui.spatial

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.glassAssembleEntrance(isVisible: Boolean): Modifier = composed {
    // A bouncy spring that overshoots slightly, mimicking physical momentum
    val springSpec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

    val scale by animateFloatAsState(targetValue = if (isVisible) 1f else 0.8f, animationSpec = springSpec)
    val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = tween(200))
    val rotationX by animateFloatAsState(targetValue = if (isVisible) 0f else -15f, animationSpec = springSpec)
    val translateY by animateFloatAsState(targetValue = if (isVisible) 0f else 50f, animationSpec = springSpec)

    this.graphicsLayer {
        this.scaleX = scale
        this.scaleY = scale
        this.alpha = alpha
        this.rotationX = rotationX
        this.translationY = translateY
        this.cameraDistance = 12f // Add perspective distortion to the rotation
    }
}
