package com.health.friday.desktop.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

// ─────────────────────────────────────────────────────────────
//  FRIDAY MOTION TOKENS
//
//  Every animation in FRIDAY ECo pulls from here.
//  Three families:
//    Durations  — how long things take (ms)
//    Easings    — the shape of the curve
//    Springs    — physical spring specs for organic motion
//
//  Rule: Never hardcode a tween duration or spring spec
//  in a composable. Always reference FridayTheme.motion.*
// ─────────────────────────────────────────────────────────────

@Immutable
data class FridayMotion(

    // ── Durations ─────────────────────────────────────────────
    /** 80ms — micro interactions: icon state flips, checkbox toggles */
    val durationMicro: Int,
    /** 150ms — fast UI feedback: hover states, button presses */
    val durationFast: Int,
    /** 250ms — standard transitions: tab changes, card reveals */
    val durationNormal: Int,
    /** 400ms — deliberate transitions: screen changes, modal enters */
    val durationEmphasis: Int,
    /** 600ms — large spatial moves: widget pop-out, window slides */
    val durationExpressive: Int,
    /** 1200ms — ambient / atmospheric: glow pulses, background shifts */
    val durationAmbient: Int,
    /** 3000ms — continuous rotation: ReactiveGlowingBorder sweep */
    val durationSweep: Int,

    // ── Tween Easings ─────────────────────────────────────────
    /** Linear — only for continuous rotation and progress bars */
    val easingLinear: Easing,
    /** Ease out — for elements entering the screen */
    val easingEmphasizedDecelerate: Easing,
    /** Ease in — for elements leaving the screen */
    val easingEmphasizedAccelerate: Easing,
    /** Ease in-out — for elements moving within the screen */
    val easingStandard: Easing,
    /** Fast ease out — for immediate responsive feedback */
    val easingSnappy: Easing,

    // ── Spring Specs ──────────────────────────────────────────
    /** Snappy spring — widget drag handles, quick snaps */
    val springSnappy: SpringSpec<Float>,
    /** Bouncy spring — orb reactions, task completion checkmarks */
    val springBouncy: SpringSpec<Float>,
    /** Gentle spring — large layout shifts, widget reordering */
    val springGentle: SpringSpec<Float>,
    /** Stiff spring — hover states, near-instant micro feedback */
    val springStiff: SpringSpec<Float>,
)

// ─────────────────────────────────────────────────────────────
//  DEFAULT MOTION VALUES
// ─────────────────────────────────────────────────────────────

val FridayDefaultMotion = FridayMotion(
    durationMicro        = 80,
    durationFast         = 150,
    durationNormal       = 250,
    durationEmphasis     = 400,
    durationExpressive   = 600,
    durationAmbient      = 1200,
    durationSweep        = 3000,

    // Material 3 Emphasized curves — feel premium, not generic
    easingLinear                  = LinearEasing,
    easingEmphasizedDecelerate    = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
    easingEmphasizedAccelerate    = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f),
    easingStandard                = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    easingSnappy                  = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f),

    // Springs: dampingRatio + stiffness
    // Low damping = more bounce. Low stiffness = slower, heavier feel.
    springSnappy = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.5
        stiffness    = Spring.StiffnessMediumLow        // 400
    ),
    springBouncy = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,    // 0.2
        stiffness    = Spring.StiffnessMedium            // 1500
    ),
    springGentle = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0
        stiffness    = Spring.StiffnessLow               // 200
    ),
    springStiff = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0
        stiffness    = Spring.StiffnessHigh              // 10_000
    ),
)

val LocalFridayMotion = staticCompositionLocalOf { FridayDefaultMotion }

// ─────────────────────────────────────────────────────────────
//  CONVENIENCE EXTENSION — build a tween from tokens
//
//  Usage:
//    val spec = FridayTheme.motion.tweenNormal<Float>()
//    val spec = FridayTheme.motion.tweenFast<Dp>(easing = motion.easingSnappy)
// ─────────────────────────────────────────────────────────────

fun <T> FridayMotion.tweenMicro(easing: Easing = easingSnappy) =
    tween<T>(durationMillis = durationMicro, easing = easing)

fun <T> FridayMotion.tweenFast(easing: Easing = easingEmphasizedDecelerate) =
    tween<T>(durationMillis = durationFast, easing = easing)

fun <T> FridayMotion.tweenNormal(easing: Easing = easingStandard) =
    tween<T>(durationMillis = durationNormal, easing = easing)

fun <T> FridayMotion.tweenEmphasis(easing: Easing = easingEmphasizedDecelerate) =
    tween<T>(durationMillis = durationEmphasis, easing = easing)

fun <T> FridayMotion.tweenExpressive(easing: Easing = easingEmphasizedDecelerate) =
    tween<T>(durationMillis = durationExpressive, easing = easing)

fun <T> FridayMotion.tweenAmbient(easing: Easing = easingLinear) =
    tween<T>(durationMillis = durationAmbient, easing = easing)
