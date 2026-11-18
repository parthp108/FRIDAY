package com.health.friday.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.health.friday.desktop.theme.FridayTheme

// ─────────────────────────────────────────────────────────────
//  REACTIVE GLOWING BORDER — updated for Phase 1 token system
//
//  CHANGED:
//    Before: hardcoded Color(0xFF00E5FF), Color(0x33FFFFFF),
//            Color(0xFF7C2D12), tween(3000)
//    After:  all values from FridayTheme tokens
//
//  New: `state` parameter replaces the binary `isActive` bool.
//  Supports 4 distinct glow states matching FridayColors:
//    Active  → cyan  (telemetry streaming)
//    Alert   → amber (warning / nudge fired)
//    Ai      → violet (inference running)
//    Idle    → dim white (connected but no data)
// ─────────────────────────────────────────────────────────────

enum class GlowState { Active, Alert, Ai, Idle }

fun Modifier.reactiveGlow(
    state: GlowState,
    shape: Shape,
): Modifier = composed {
    val colors = FridayTheme.colors
    val motion = FridayTheme.motion
    val glow   = FridayTheme.glow

    // Continuous rotation — speed from token
    val infiniteTransition = rememberInfiniteTransition(label = "glow_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(motion.durationSweep, easing = motion.easingLinear),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation_anim",
    )

    // Resolve the two gradient colors from semantic tokens
    val (leadColor, trailColor) = when (state) {
        GlowState.Active -> colors.accent      to colors.accentMuted
        GlowState.Alert  -> colors.statusWarning to Color.Transparent
        GlowState.Ai     -> colors.statusAi    to Color.Transparent
        GlowState.Idle   -> colors.glassEdgeSoft to Color.Transparent
    }

    val animatedLead  by animateColorAsState(
        targetValue  = leadColor,
        animationSpec = tween(motion.durationNormal),
        label = "lead_color",
    )
    val animatedTrail by animateColorAsState(
        targetValue  = trailColor,
        animationSpec = tween(motion.durationNormal),
        label = "trail_color",
    )

    this
        .padding(glow.border)
        .drawWithCache {
            val brush = Brush.sweepGradient(
                colors = listOf(animatedLead, animatedTrail, animatedLead),
            )
            onDrawWithContent {
                drawContent()
                rotate(rotation) {
                    drawOutline(
                        outline    = shape.createOutline(size, layoutDirection, this),
                        brush      = brush,
                        style      = Stroke(width = glow.border.toPx()),
                        blendMode  = BlendMode.Screen,
                    )
                }
            }
        }
}

// ─────────────────────────────────────────────────────────────
//  BACKWARD-COMPAT OVERLOAD
//  Old callers that pass `isActive: Boolean` still compile.
//  Migrate to GlowState when convenient.
// ─────────────────────────────────────────────────────────────

fun Modifier.reactiveGlow(
    isActive: Boolean,
    shape: Shape,
): Modifier = reactiveGlow(
    state = if (isActive) GlowState.Active else GlowState.Idle,
    shape = shape,
)
