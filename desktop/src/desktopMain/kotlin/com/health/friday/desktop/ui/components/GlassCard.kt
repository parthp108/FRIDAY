package com.health.friday.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.health.friday.desktop.theme.FridayTheme

// ─────────────────────────────────────────────────────────────
//  GLASS CARD  — updated for Phase 1 token system
//
//  CHANGED:
//    Before: hardcoded Color(0x660F172A) and Color(0x1AFFFFFF)
//    After:  FridayTheme.colors.glassFill + glassEdgeSoft
//
//  This means changing the glass style project-wide is now
//  a one-line change in FridayColors.kt.
// ─────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = FridayTheme.shapes.radiusLg,
    edgeBrightness: GlassEdge = GlassEdge.Soft,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = FridayTheme.colors
    val shape  = RoundedCornerShape(cornerRadius)

    val borderColor = when (edgeBrightness) {
        GlassEdge.Bright -> colors.glassEdgeBright
        GlassEdge.Soft   -> colors.glassEdgeSoft
        GlassEdge.Dim    -> colors.glassEdgeDim
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.glassFill)
            .border(FridayTheme.glow.hairline, borderColor, shape),
        content = content,
    )
}

/** Controls how visible the glass card's edge highlight is. */
enum class GlassEdge { Bright, Soft, Dim }
