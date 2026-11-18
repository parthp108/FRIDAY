package com.health.friday.voice.pipeline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AmbientAudioPipeline(
    private val vadEngine: SileroVadEngine,
    private val wakewordEngine: WakewordEngine,
    private val asrEngine: StreamingAsrEngine
) {
    private val sampleRate = 16000
    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioRecord: AudioRecord? = null
    private val isListening = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _transcriptionFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val transcriptionFlow: SharedFlow<String> = _transcriptionFlow.asSharedFlow()

    private val _wakewordFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 5)
    val wakewordFlow: SharedFlow<Unit> = _wakewordFlow.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startAmbientMesh() {
        if (isListening.getAndSet(true)) return

        val bufferSizeToUse = if (minBufferSize > 0) minBufferSize * 4 else 8192

        runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeToUse
            )
            audioRecord?.startRecording()
        }

        scope.launch {
            val audioBuffer = ShortArray(512) // ~32ms frames at 16kHz
            var isWakewordActive = false
            var silenceFrames = 0

            while (isListening.get()) {
                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readResult <= 0) continue

                // Stage 0: Ultra-fast RMS calculation. Skip VAD if room is silent.
                val rms = calculateRms(audioBuffer, readResult)
                if (rms < 50.0 && !isWakewordActive) continue

                // Stage 1: Silero Voice Activity Detection (VAD)
                val speechProbability = vadEngine.evaluate(audioBuffer)
                val hasSpeech = speechProbability > 0.5f

                if (!isWakewordActive) {
                    if (hasSpeech) {
                        // Stage 2: Keyword Spotting (Wakeword)
                        if (wakewordEngine.detect(audioBuffer)) {
                            isWakewordActive = true
                            asrEngine.reset()
                            _wakewordFlow.tryEmit(Unit)
                            playSubtleEarcon()
                        }
                    }
                } else {
                    // Stage 3: Streaming ASR
                    asrEngine.feedAudio(audioBuffer)

                    if (!hasSpeech) {
                        silenceFrames++
                        // ~800ms of silence (25 frames * 32ms) triggers completion
                        if (silenceFrames > 25) {
                            val finalTranscription = asrEngine.finalizeStream()
                            if (finalTranscription.isNotBlank()) {
                                _transcriptionFlow.emit(finalTranscription)
                            }
                            isWakewordActive = false
                            silenceFrames = 0
                        }
                    } else {
                        silenceFrames = 0
                    }
                }
            }
        }
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val valDouble = buffer[i].toDouble()
            sum += valDouble * valDouble
        }
        return sqrt(sum / length)
    }

    fun stop() {
        isListening.set(false)
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        scope.cancel()
    }

    private fun playSubtleEarcon() {
        // Subtle audio or haptic feedback notification
    }
}
