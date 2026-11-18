package com.health.friday.predictive

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.DailySignalsDao

class PredictProductivityTool(
    private val predictiveEngine: PredictiveEngine,
    private val signalsDao: DailySignalsDao
) : AiTool {
    override val name = "predict_productivity"
    override val description = "Forecasts how many tasks the user is likely to complete today based on their sleep last night using linear regression."
    override val parameters = emptyList<AiToolParameter>()

    override suspend fun execute(arguments: Map<String, String>): String {
        val recent = signalsDao.getRecentSignals(30).sortedByDescending { it.date }
        if (recent.size < 5) return "Not enough historical data to run regression."

        val today = recent.first()
        val sleep = today.sleepMinutes ?: return "No sleep data logged for today to predict from."

        val predictedTasks = predictiveEngine.predictProductivity(sleep, recent.drop(1))
        val formattedTasks = String.format("%.1f", predictedTasks)

        return "Based on ${sleep / 60}h ${sleep % 60}m of sleep, regression models predict the completion of ~$formattedTasks tasks today."
    }
}