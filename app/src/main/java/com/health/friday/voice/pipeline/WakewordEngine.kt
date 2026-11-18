package com.health.friday.voice.pipeline

import android.content.Context
import kotlin.math.abs

class WakewordEngine(private val context: Context) {
    private var energyHistory = FloatArray(10)
    private var historyIndex = 0

    fun detect(audioBuffer: ShortArray): Boolean {
        var sum = 0L
        for (i in audioBuffer.indices) {
            val sample = abs(audioBuffer[i].toInt())
            sum += sample
        }
        val avgEnergy = sum.toFloat() / audioBuffer.size.toFloat()

        energyHistory[historyIndex] = avgEnergy
        historyIndex = (historyIndex + 1) % energyHistory.size

        var averagePastEnergy = 0f
        for (energy in energyHistory) {
            averagePastEnergy += energy
        }
        averagePastEnergy /= energyHistory.size

        // Keyword detection trigger when energy peak exceeds historical average energy threshold
        return avgEnergy > 1500f && avgEnergy > (averagePastEnergy * 2.2f)
    }
}
