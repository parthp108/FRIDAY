package com.health.friday.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// ─────────────────────────────────────────────────────────────
//  FRIDAY THEME  — the single entry point for the design system
//
//  REPLACES: the old Theme.kt that wrapped MaterialTheme directly.
//
//  Usage:
//    FridayTheme {
//        // your composables here
//    }
//
//  Access tokens anywhere in the tree:
//    FridayTheme.colors.accent
//    FridayTheme.motion.springSnappy
//    FridayTheme.shapes.shapeLg
//    FridayTheme.spacing.lg
//    FridayTheme.glow.border
// ─────────────────────────────────────────────────────────────

@Composable
fun FridayTheme(
    colors:  FridayColors  = FridayDarkColors,
    motion:  FridayMotion  = FridayDefaultMotion,
    shapes:  FridayShapes  = FridayDefaultShapes,
    spacing: FridaySpacing = FridayDefaultSpacing,
    glow:    FridayGlow    = FridayDefaultGlow,
    content: @Composable () -> Unit,
) {
    // Bridge to Material3 so existing Material composables still work
    val m3Colors = darkColorScheme(
        primary         = colors.accent,
        onPrimary       = colors.onAccent,
        background      = colors.bgPrimary,
        surface         = colors.bgElevated2,
        onSurface       = colors.textPrimary,
        onSurfaceVariant= colors.textSecondary,
        error           = colors.statusError,
        onError         = colors.textOnAccent,
        outline         = colors.glassEdgeSoft,
    )

    CompositionLocalProvider(
        LocalFridayColors  provides colors,
        LocalFridayMotion  provides motion,
        LocalFridayShapes  provides shapes,
        LocalFridaySpacing provides spacing,
        LocalFridayGlow    provides glow,
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content     = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  STATIC ACCESSOR OBJECT
//  Mirrors how MaterialTheme works — read tokens without
//  needing to pass them down as parameters.
// ─────────────────────────────────────────────────────────────

object FridayTheme {
    val colors: FridayColors
        @Composable @ReadOnlyComposable
        get() = LocalFridayColors.current

    val motion: FridayMotion
        @Composable @ReadOnlyComposable
        get() = LocalFridayMotion.current

    val shapes: FridayShapes
        @Composable @ReadOnlyComposable
        get() = LocalFridayShapes.current

    val spacing: FridaySpacing
        @Composable @ReadOnlyComposable
        get() = LocalFridaySpacing.current

    val glow: FridayGlow
        @Composable @ReadOnlyComposable
        get() = LocalFridayGlow.current
}
