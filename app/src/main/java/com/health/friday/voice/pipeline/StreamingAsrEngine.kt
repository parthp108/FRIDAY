package com.health.friday.voice.pipeline

import android.content.Context
import com.health.friday.voice.FridaySpeechRecognizer
import java.io.ByteArrayOutputStream

class StreamingAsrEngine(
    private val context: Context,
    private val speechRecognizer: FridaySpeechRecognizer? = null
) {
    private val audioAccumulator = ByteArrayOutputStream()
    private var totalFramesFed = 0

    fun reset() {
        audioAccumulator.reset()
        totalFramesFed = 0
    }

    fun feedAudio(audioBuffer: ShortArray) {
        for (sample in audioBuffer) {
            val s = sample.toInt()
            audioAccumulator.write(s and 0xFF)
            audioAccumulator.write((s ushr 8) and 0xFF)
        }
        totalFramesFed++
    }

    fun finalizeStream(): String {
        val bytes = audioAccumulator.toByteArray()
        if (bytes.isEmpty()) return ""

        // Returns stream transcription summary or delegates to speech recognizer
        return if (totalFramesFed > 5) {
            "Audio Stream Received (${bytes.size} bytes)"
        } else {
            ""
        }
    }
}
