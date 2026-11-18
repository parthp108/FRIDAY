package com.health.friday.desktop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

// 1. The Tactile Hover Glow
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.hoverMicroGlow(glowColor: Color = Color(0x4D00E5FF)): Modifier = composed {
    var isHovered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
    )

    this
        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
        .onPointerEvent(PointerEventType.Exit) { isHovered = false }
        .drawWithCache {
            val radialBrush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.width.coerceAtLeast(size.height)
            )
            onDrawBehind {
                drawRect(brush = radialBrush, alpha = alpha, blendMode = BlendMode.Screen)
            }
        }
}

// 2. Premium Skeleton Shimmer (Replaces Loading Spinners)
fun Modifier.shimmerSkeleton(): Modifier = composed {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = -1000f, targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    this.drawWithCache {
        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                Color(0x1AFFFFFF), // Base glass
                Color(0x4DFFFFFF), // Bright sweeping highlight
                Color(0x1AFFFFFF)
            ),
            start = Offset(translateAnim, translateAnim),
            end = Offset(translateAnim + 400f, translateAnim + 400f)
        )
        onDrawWithContent {
            // Draws the sweep over the element
            drawRect(brush = shimmerBrush)
        }
    }
}
