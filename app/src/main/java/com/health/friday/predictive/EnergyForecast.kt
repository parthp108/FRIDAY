package com.health.friday.predictive

data class EnergyForecast(
    val crashRiskLevel: String, // "LOW", "MODERATE", "HIGH", "SEVERE"
    val crashTimeEstimate: String?, // e.g. "14:30"
    val optimalFocusWindow: String, // e.g. "09:00 - 11:30"
    val reason: String
)