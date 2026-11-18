package com.health.friday.desktop.features.sensory

import kotlinx.coroutines.delay

class SensoryEngine(private val audio: ProceduralAudioSynthesizer) {

    var isSensoryEnabled: Boolean = true

    /** Crystalline chord when the Command Surface snaps open */
    suspend fun playCommandOpen() {
        if (!isSensoryEnabled) return

        // Synthesizes a beautiful major 7th chord (Cmaj7)
        audio.playSpatialTone(523.25f, 400, panX = 0f, attackMs = 10) // C5
        delay(20)
        audio.playSpatialTone(659.25f, 500, panX = -0.2f, attackMs = 10) // E5
        delay(20)
        audio.playSpatialTone(783.99f, 600, panX = 0.2f, attackMs = 10) // G5
        delay(30)
        audio.playSpatialTone(987.77f, 800, panX = 0f, attackMs = 30) // B5

        triggerHaptic(HapticIntensity.LIGHT)
    }

    /** Low, deep hum when the AI Orb is actively thinking */
    fun playOrbProcessing(orbScreenPositionX: Float, screenWidth: Float) {
        if (!isSensoryEnabled) return
        // Calculate stereo panning based on where the Orb physically is on the monitor
        val panX = if (screenWidth > 0) ((orbScreenPositionX / screenWidth) * 2f) - 1f else 0f

        // Deep bass frequency
        audio.playSpatialTone(65.41f, 1500, panX = panX, attackMs = 300)
        triggerHaptic(HapticIntensity.RUMBLE)
    }

    /** Crisp snap when clicking a widget */
    fun playWidgetSnap() {
        if (!isSensoryEnabled) return
        audio.playSpatialTone(1200f, 40, attackMs = 2)
        triggerHaptic(HapticIntensity.CRISP)
    }

    enum class HapticIntensity { LIGHT, CRISP, RUMBLE }

    private fun triggerHaptic(intensity: HapticIntensity) {
        // Bridges to OS native haptics or logs intent
    }
}
