package com.health.friday.desktop.features.sensory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ProceduralAudioSynthesizer {
    // Professional standard: 44.1kHz, 16-bit depth, Stereo, Signed, Little-Endian
    private val format = AudioFormat(44100f, 16, 2, true, false)

    /**
     * Synthesizes a tone in real-time.
     * @param frequency The pitch in Hz (e.g., 440f for A4)
     * @param durationMs Length of the sound
     * @param panX Spatial location from -1.0 (hard left) to 1.0 (hard right)
     * @param attackMs How fast the sound fades in (prevents clicking)
     */
    fun playSpatialTone(frequency: Float, durationMs: Long, panX: Float = 0f, attackMs: Long = 20) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
                line.open(format)
                line.start()

                val totalFrames = (durationMs * 44.1f).toInt()
                val attackFrames = (attackMs * 44.1f).toInt()
                val releaseFrames = totalFrames / 3 // 33% of the sound is the fade-out

                val buffer = ByteArray(totalFrames * 4) // 2 bytes per channel * 2 channels

                // Stereo Panning Math (Constant Power Panning Rule)
                val leftVol = cos((panX + 1) * PI / 4).toFloat()
                val rightVol = sin((panX + 1) * PI / 4).toFloat()

                for (i in 0 until totalFrames) {
                    val time = i / 44100.0

                    // ADSR Envelope calculation
                    val envelope = when {
                        i < attackFrames -> i.toFloat() / attackFrames // Fade in
                        i > totalFrames - releaseFrames -> (totalFrames - i).toFloat() / releaseFrames // Fade out
                        else -> 1f // Sustain
                    }

                    // Generate raw Sine wave
                    val rawSine = sin(2.0 * PI * frequency * time) * 30000 * envelope // Max 16-bit amplitude is 32767

                    // Apply Panning
                    val leftSample = (rawSine * leftVol).toInt().toShort()
                    val rightSample = (rawSine * rightVol).toInt().toShort()

                    // Interleave L/R channels into byte array (Little Endian)
                    val byteIndex = i * 4
                    buffer[byteIndex] = (leftSample.toInt() and 0xFF).toByte()
                    buffer[byteIndex + 1] = ((leftSample.toInt() shr 8) and 0xFF).toByte()
                    buffer[byteIndex + 2] = (rightSample.toInt() and 0xFF).toByte()
                    buffer[byteIndex + 3] = ((rightSample.toInt() shr 8) and 0xFF).toByte()
                }

                line.write(buffer, 0, buffer.size)
                line.drain()
                line.close()
            } catch (ex: Exception) {
                // Ignore audio line exceptions if audio system is unavailable
            }
        }
    }
}
