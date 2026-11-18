package com.health.friday.desktop.features.intelligence

enum class WidgetMode {
    HIDDEN,    // Completely removed from the dashboard
    MINIMAL,   // A tiny glanceable icon/ring
    EXPANDED,  // Standard view with text and data
    IMMERSIVE  // Max width, heavy charting, commands attention
}

enum class PriorityLevel(val weight: Int) {
    BACKGROUND(0),
    STANDARD(1),
    ELEVATED(2),
    CRITICAL(3) // Forces widget to the absolute top left of the masonry grid
}

data class WidgetIntelligenceState(
    val mode: WidgetMode,
    val priority: PriorityLevel,
    val overrideReason: String? = null // e.g., "Elevated Stress/HR"
)
