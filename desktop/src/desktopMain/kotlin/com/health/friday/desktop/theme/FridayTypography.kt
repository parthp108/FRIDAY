package com.health.friday.desktop.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// In production, these map to bundled TTF/OTF files (e.g., Inter, JetBrains Mono)
object FridayType {
    val Geometric = FontFamily.SansSerif // For strict UI data (Time, BPM, Temperatures)
    val Humanist = FontFamily.SansSerif  // For the AI's conversational voice
    val Mono = FontFamily.Monospace      // For code, logs, and technical output
}

object FridayTypography {
    val DataEnormous = TextStyle(
        fontFamily = FridayType.Geometric,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        letterSpacing = (-1.5).sp // Tighter tracking for large numbers
    )

    val ConversationalAI = TextStyle(
        fontFamily = FridayType.Humanist,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    )

    val CodeTechnical = TextStyle(
        fontFamily = FridayType.Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )
}
