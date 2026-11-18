package com.health.friday.desktop.ui.spatial

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.spatialParallax(
    maxTiltDegrees: Float = 1.0f,
    cameraDistance: Float = 16f
): Modifier = composed {
    var size by remember { mutableStateOf(Offset.Zero) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    var isHovering by remember { mutableStateOf(false) }

    // Calculate relative tilt (-1.0 to 1.0) based on mouse distance from center
    val targetRotationX = if (isHovering && size.y > 0) {
        ((pointerOffset.y - (size.y / 2)) / (size.y / 2)) * -maxTiltDegrees
    } else 0f

    val targetRotationY = if (isHovering && size.x > 0) {
        ((pointerOffset.x - (size.x / 2)) / (size.x / 2)) * maxTiltDegrees
    } else 0f

    // Spring physics ensure the tilt feels heavy and liquid, not jittery
    val rotationX by animateFloatAsState(targetRotationX, spring(stiffness = Spring.StiffnessLow))
    val rotationY by animateFloatAsState(targetRotationY, spring(stiffness = Spring.StiffnessLow))

    this
        .onGloballyPositioned { coordinates ->
            size = Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        }
        .onPointerEvent(PointerEventType.Move) { event ->
            pointerOffset = event.changes.first().position
            isHovering = true
        }
        .onPointerEvent(PointerEventType.Exit) {
            isHovering = false
        }
        .graphicsLayer {
            this.rotationX = rotationX
            this.rotationY = rotationY
            this.cameraDistance = cameraDistance // Gives true perspective distortion
        }
}
