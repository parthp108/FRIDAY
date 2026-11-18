package com.health.friday.desktop.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
//  FRIDAY PRIMITIVE PALETTE
//  Raw color values. Never use these directly in UI code.
//  Always go through FridayColors semantic tokens below.
// ─────────────────────────────────────────────────────────────

private object Primitive {
    // Cyan family (primary identity color)
    val Cyan400   = Color(0xFF00E5FF)
    val Cyan500   = Color(0xFF00B8D4)
    val Cyan600   = Color(0xFF0097A7)
    val CyanGlow  = Color(0x4000E5FF)  // 25% — ambient glow halos
    val CyanDim   = Color(0x1A00E5FF)  // 10% — subtle surface tints

    // Amber family (alert / warning state)
    val Amber400  = Color(0xFFFFB300)
    val AmberGlow = Color(0x40FFB300)

    // Rose family (error / critical state)
    val Rose500   = Color(0xFFFF3B30)
    val RoseGlow  = Color(0x40FF3B30)

    // Violet family (AI processing state)
    val Violet400 = Color(0xFFAB47BC)
    val VioletGlow= Color(0x40AB47BC)

    // Emerald family (success / healthy state)
    val Emerald400= Color(0xFF00E676)
    val EmeraldGlow=Color(0x4000E676)

    // Space darks (backgrounds & surfaces)
    val Space950  = Color(0xFF050810)  // Deepest — window root
    val Space900  = Color(0xFF0B0F19)  // Deep Space — primary bg
    val Space800  = Color(0xFF0F1726)  // Elevated layer 1
    val Space700  = Color(0xFF131B2B)  // Elevated layer 2 — cards
    val Space600  = Color(0xFF192334)  // Elevated layer 3 — popovers
    val Space500  = Color(0xFF1E2A3A)  // Elevated layer 4 — hover

    // Glass overlays (semi-transparent layers)
    val Glass40   = Color(0x660F172A)  // 40% — standard glass card
    val Glass25   = Color(0x400F172A)  // 25% — lighter glass
    val Glass10   = Color(0x1A0F172A)  // 10% — ghost layer

    // Edge highlights (glass border illusions)
    val EdgeBright= Color(0x33FFFFFF)  // 20% white — bright edge
    val EdgeSoft  = Color(0x1AFFFFFF)  // 10% white — soft edge
    val EdgeDim   = Color(0x0DFFFFFF)  //  5% white — barely visible

    // Text
    val White     = Color(0xFFFFFFFF)
    val Gray100   = Color(0xFFE2E8F0)  // Primary text
    val Gray200   = Color(0xFFB0BEC5)  // Secondary text
    val Gray400   = Color(0xFF607D8B)  // Muted / disabled text
    val Gray600   = Color(0xFF37474F)  // Placeholder text
}

// ─────────────────────────────────────────────────────────────
//  FRIDAY SEMANTIC COLOR TOKENS
//  These are what every composable in the app should use.
//  Named by role, not by appearance.
// ─────────────────────────────────────────────────────────────

@Immutable
data class FridayColors(

    // ── Identity ──────────────────────────────────────────────
    /** The primary brand color. Used for active states, key CTAs, the orb core. */
    val accent: Color,
    /** Softer version of accent. Used for secondary highlights. */
    val accentMuted: Color,
    /** Accent at zero saturation — icon on accent background. */
    val onAccent: Color,

    // ── Glow Colors ───────────────────────────────────────────
    /** The cyan halo behind the active orb, active widget border, etc. */
    val glowActive: Color,
    /** Amber halo — alert / warning state glows. */
    val glowAlert: Color,
    /** Rose halo — error / critical state glows. */
    val glowError: Color,
    /** Violet halo — AI thinking / processing state glows. */
    val glowAi: Color,
    /** Emerald halo — healthy / success state glows. */
    val glowSuccess: Color,

    // ── Backgrounds ───────────────────────────────────────────
    /** The absolute root — the transparent window sits on this logically. */
    val bgRoot: Color,
    /** Primary background — main HUD area. */
    val bgPrimary: Color,
    /** First elevation — widget grid background. */
    val bgElevated1: Color,
    /** Second elevation — card backgrounds. */
    val bgElevated2: Color,
    /** Third elevation — popover / dropdown backgrounds. */
    val bgElevated3: Color,
    /** Hover state surface — slightly lighter than bgElevated2. */
    val bgHover: Color,

    // ── Glass Surfaces ────────────────────────────────────────
    /** Standard glass card fill — 40% opacity deep blue. */
    val glassFill: Color,
    /** Light glass fill — 25% opacity. Use for nested glass. */
    val glassFillLight: Color,
    /** Ghost glass fill — 10% opacity. Use for overlays. */
    val glassFillGhost: Color,
    /** Glass card border — bright top/left edge illusion. */
    val glassEdgeBright: Color,
    /** Glass card border — standard soft edge. */
    val glassEdgeSoft: Color,
    /** Glass card border — almost invisible edge. */
    val glassEdgeDim: Color,

    // ── Text ──────────────────────────────────────────────────
    /** Primary body text — full contrast. */
    val textPrimary: Color,
    /** Secondary / supporting text — lower contrast. */
    val textSecondary: Color,
    /** Muted text — disabled states, timestamps, metadata. */
    val textMuted: Color,
    /** Placeholder text — input hint text. */
    val textPlaceholder: Color,
    /** Text on accent/colored backgrounds. */
    val textOnAccent: Color,

    // ── Status Semantic Colors ────────────────────────────────
    /** Active / healthy / running state. */
    val statusActive: Color,
    /** Warning / approaching limit state. */
    val statusWarning: Color,
    /** Error / failure / critical state. */
    val statusError: Color,
    /** AI / inference / processing state. */
    val statusAi: Color,
    /** Success / completed / good state. */
    val statusSuccess: Color,
    /** Idle / inactive / no-data state. */
    val statusIdle: Color,
)

// ─────────────────────────────────────────────────────────────
//  DEFAULT DARK THEME  (the only theme for now)
// ─────────────────────────────────────────────────────────────

val FridayDarkColors = FridayColors(
    accent              = Primitive.Cyan400,
    accentMuted         = Primitive.Cyan600,
    onAccent            = Primitive.Space900,

    glowActive          = Primitive.CyanGlow,
    glowAlert           = Primitive.AmberGlow,
    glowError           = Primitive.RoseGlow,
    glowAi              = Primitive.VioletGlow,
    glowSuccess         = Primitive.EmeraldGlow,

    bgRoot              = Primitive.Space950,
    bgPrimary           = Primitive.Space900,
    bgElevated1         = Primitive.Space800,
    bgElevated2         = Primitive.Space700,
    bgElevated3         = Primitive.Space600,
    bgHover             = Primitive.Space500,

    glassFill           = Primitive.Glass40,
    glassFillLight      = Primitive.Glass25,
    glassFillGhost      = Primitive.Glass10,
    glassEdgeBright     = Primitive.EdgeBright,
    glassEdgeSoft       = Primitive.EdgeSoft,
    glassEdgeDim        = Primitive.EdgeDim,

    textPrimary         = Primitive.Gray100,
    textSecondary       = Primitive.Gray200,
    textMuted           = Primitive.Gray400,
    textPlaceholder     = Primitive.Gray600,
    textOnAccent        = Primitive.Space900,

    statusActive        = Primitive.Cyan400,
    statusWarning       = Primitive.Amber400,
    statusError         = Primitive.Rose500,
    statusAi            = Primitive.Violet400,
    statusSuccess       = Primitive.Emerald400,
    statusIdle          = Primitive.Gray400,
)

// ─────────────────────────────────────────────────────────────
//  COMPOSITION LOCAL
//  Access via LocalFridayColors.current anywhere in the tree.
// ─────────────────────────────────────────────────────────────

val LocalFridayColors = staticCompositionLocalOf { FridayDarkColors }
