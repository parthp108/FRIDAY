package com.health.friday.desktop.theme

import androidx.compose.ui.graphics.Color

enum class AmbientState(val primaryHue: Color, val secondaryHue: Color, val pulseSpeedMs: Int) {
    IDLE(
        primaryHue = Color(0xFF0F172A), // Very Dark Blue
        secondaryHue = Color(0xFF000000), // Pure Black
        pulseSpeedMs = 8000
    ),
    THINKING(
        primaryHue = Color(0xFF004D40), // Deep Teal
        secondaryHue = Color(0xFF00E5FF), // Cyber Cyan Edge
        pulseSpeedMs = 3000
    ),
    FOCUS(
        primaryHue = Color(0xFF1A237E), // Deep Indigo
        secondaryHue = Color(0xFF5C6BC0), // Soft Blue
        pulseSpeedMs = 12000
    ),
    ALERT(
        primaryHue = Color(0xFF3E2723), // Dark Amber/Brown
        secondaryHue = Color(0xFFFF8F00), // Urgent Orange
        pulseSpeedMs = 1500
    )
}
