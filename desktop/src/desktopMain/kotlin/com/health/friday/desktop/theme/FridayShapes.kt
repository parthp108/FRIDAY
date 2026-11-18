package com.health.friday.desktop.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────
//  FRIDAY SHAPE TOKENS
// ─────────────────────────────────────────────────────────────

@Immutable
data class FridayShapes(
    /** 4dp — tags, badges, tiny chips */
    val radiusXs: Dp,
    /** 8dp — small buttons, tooltips */
    val radiusSm: Dp,
    /** 12dp — input fields, small cards */
    val radiusMd: Dp,
    /** 16dp — standard GlassCard, widget containers */
    val radiusLg: Dp,
    /** 20dp — large modals, expanded panels */
    val radiusXl: Dp,
    /** 28dp — the orb window, pill buttons */
    val radiusPill: Dp,
    /** 50% — perfect circle (use with equal width/height) */
    val radiusCircle: Dp,

    // Pre-built shapes from the radii above
    val shapeXs: RoundedCornerShape,
    val shapeSm: RoundedCornerShape,
    val shapeMd: RoundedCornerShape,
    val shapeLg: RoundedCornerShape,
    val shapeXl: RoundedCornerShape,
    val shapePill: RoundedCornerShape,
)

// ─────────────────────────────────────────────────────────────
//  SPACING TOKENS
//  Used for padding, gap, margin. All multiples of 4dp.
// ─────────────────────────────────────────────────────────────

@Immutable
data class FridaySpacing(
    val xs:   Dp,   //  4dp
    val sm:   Dp,   //  8dp
    val md:   Dp,   // 12dp
    val lg:   Dp,   // 16dp
    val xl:   Dp,   // 24dp
    val xxl:  Dp,   // 32dp
    val xxxl: Dp,   // 48dp
    val huge: Dp,   // 64dp
)

// ─────────────────────────────────────────────────────────────
//  GLOW / BLUR TOKENS
//  Sizes for shadow / glow effects.
// ─────────────────────────────────────────────────────────────

@Immutable
data class FridayGlow(
    /** 1dp — hairline border glow, barely visible */
    val hairline: Dp,
    /** 2dp — standard ReactiveGlowingBorder width */
    val border: Dp,
    /** 4dp — hover state glow halo */
    val halo: Dp,
    /** 8dp — focused element glow */
    val focus: Dp,
    /** 16dp — active orb inner glow */
    val orb: Dp,
    /** 32dp — ambient background glow radius */
    val ambient: Dp,
)

// ─────────────────────────────────────────────────────────────
//  DEFAULT VALUES
// ─────────────────────────────────────────────────────────────

val FridayDefaultShapes = FridayShapes(
    radiusXs     = 4.dp,
    radiusSm     = 8.dp,
    radiusMd     = 12.dp,
    radiusLg     = 16.dp,
    radiusXl     = 20.dp,
    radiusPill   = 28.dp,
    radiusCircle = 999.dp,

    shapeXs      = RoundedCornerShape(4.dp),
    shapeSm      = RoundedCornerShape(8.dp),
    shapeMd      = RoundedCornerShape(12.dp),
    shapeLg      = RoundedCornerShape(16.dp),
    shapeXl      = RoundedCornerShape(20.dp),
    shapePill    = RoundedCornerShape(28.dp),
)

val FridayDefaultSpacing = FridaySpacing(
    xs    = 4.dp,
    sm    = 8.dp,
    md    = 12.dp,
    lg    = 16.dp,
    xl    = 24.dp,
    xxl   = 32.dp,
    xxxl  = 48.dp,
    huge  = 64.dp,
)

val FridayDefaultGlow = FridayGlow(
    hairline = 1.dp,
    border   = 2.dp,
    halo     = 4.dp,
    focus    = 8.dp,
    orb      = 16.dp,
    ambient  = 32.dp,
)

val LocalFridayShapes  = staticCompositionLocalOf { FridayDefaultShapes }
val LocalFridaySpacing = staticCompositionLocalOf { FridayDefaultSpacing }
val LocalFridayGlow    = staticCompositionLocalOf { FridayDefaultGlow }
