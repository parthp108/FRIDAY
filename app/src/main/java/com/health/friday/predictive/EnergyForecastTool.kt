package com.health.friday.predictive

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class EnergyForecastTool(
    private val predictiveEngine: PredictiveEngine
) : AiTool {
    override val name = "predict_energy"
    override val description = "Predicts the user's energy levels, focus windows, and crash risk for today based on recent sleep debt and nutrition."
    override val parameters = emptyList<AiToolParameter>()

    override suspend fun execute(arguments: Map<String, String>): String {
        val forecast = predictiveEngine.forecastEnergy()

        return buildString {
            append("Energy Forecast for Today:\n")
            append("- Crash Risk: ${forecast.crashRiskLevel}\n")
            if (forecast.crashTimeEstimate != null) {
                append("- Predicted Dip/Crash Time: ${forecast.crashTimeEstimate}\n")
            }
            append("- Optimal Focus Window: ${forecast.optimalFocusWindow}\n")
            append("- Reason: ${forecast.reason}")
        }
    }
}