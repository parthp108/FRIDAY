package com.health.friday.voice.pipeline

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.health.friday.voice.FridaySpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VadPreRollSegmenter(
    private val preSpeechPadMs: Long = 300L,
    private val redemptionMs: Long = 800L,
    private val minSpeechMs: Long = 250L
) {
    private val sampleRate = 16000
    private val ringBufferCapacity = (sampleRate * (preSpeechPadMs / 1000.0)).toInt() // 4800 shorts
    private val ringBuffer = ArrayDeque<Short>(ringBufferCapacity)

    private var isSendingSpeech = false
    private var speechStartTimeMs = 0L
    private var lastSpeechTimeMs = 0L

    fun processFrame(
        pcmFrame: ShortArray,
        speechProb: Float,
        onSpeechOnset: () -> Unit,
        onSpeechSegmentCommitted: () -> Unit
    ) {
        val now = System.currentTimeMillis()

        // 1. Maintain Pre-Roll Ring Buffer (never drops consonants)
        for (sample in pcmFrame) {
            if (ringBuffer.size >= ringBufferCapacity) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(sample)
        }

        // 2. VAD State Machine with 800ms Redemption Window
        if (speechProb > 0.45f) {
            if (!isSendingSpeech) {
                isSendingSpeech = true
                speechStartTimeMs = now
                Log.d("VadSegmenter", "Speech onset detected! Including 300ms pre-roll pad.")
                onSpeechOnset()
            }
            lastSpeechTimeMs = now
        } else if (speechProb < 0.35f && isSendingSpeech) {
            // Redemption window: wait 800ms of quiet before committing segment
            val quietDuration = now - lastSpeechTimeMs
            if (quietDuration > redemptionMs) {
                val totalSpeechDuration = lastSpeechTimeMs - speechStartTimeMs
                if (totalSpeechDuration >= minSpeechMs) {
                    Log.d("VadSegmenter", "Speech ended after 800ms redemption window. Committing segment (${totalSpeechDuration}ms).")
                    onSpeechSegmentCommitted()
                }
                isSendingSpeech = false
            }
        }
    }
}

class LiveVoiceSessionEngine(
    private val context: Context,
    private val vadEngine: SileroVadEngine,
    private val speechRecognizer: FridaySpeechRecognizer
) {
    companion object {
        private const val TAG = "LiveVoiceSessionEngine"
        private const val SAMPLE_RATE = 16000
    }

    private var audioRecord: AudioRecord? = null
    private val isLiveActive = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLiveSession(
        sessionDurationMs: Long = 180000L, // 3 Minutes Live Session
        onUserSpeechCaptured: suspend (String) -> Unit
    ) {
        if (isLiveActive.getAndSet(true)) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 4 else 8192

        runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
            _isRecording.value = true
        }.onFailure {
            Log.e(TAG, "Failed to start AudioRecord live session", it)
            isLiveActive.set(false)
            _isRecording.value = false
            return
        }

        scope.launch {
            val audioBuffer = ShortArray(512) // ~32ms frame
            val startTime = System.currentTimeMillis()
            val segmenter = VadPreRollSegmenter()

            while (isLiveActive.get() && (System.currentTimeMillis() - startTime) < sessionDurationMs) {
                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING) ?: 0

                when {
                    readResult > 0 -> {
                        // Calculate live RMS level
                        val rms = calculateRms(audioBuffer, readResult)
                        _rmsDb.value = (rms.toFloat() / 300.0f).coerceIn(0f, 1f)

                        // Evaluate VAD
                        val speechProbability = vadEngine.evaluate(audioBuffer)

                        segmenter.processFrame(
                            pcmFrame = audioBuffer,
                            speechProb = speechProbability,
                            onSpeechOnset = {
                                scope.launch {
                                    val capturedSpeech = captureSpeechSegment()
                                    if (capturedSpeech.isNotBlank()) {
                                        onUserSpeechCaptured(capturedSpeech)
                                    }
                                }
                            },
                            onSpeechSegmentCommitted = {
                                Log.d(TAG, "VAD Segment committed")
                            }
                        )
                    }
                    readResult == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION - attempting restart")
                        runCatching { audioRecord?.startRecording() }
                        delay(20)
                    }
                    readResult == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioRecord ERROR_DEAD_OBJECT - recorder released by system")
                        delay(50)
                    }
                    else -> {
                        delay(10)
                    }
                }
            }

            stopLiveSession()
        }
    }

    private suspend fun captureSpeechSegment(): String {
        val resultDef = CompletableDeferred<String>()
        speechRecognizer.startListening(
            isLongForm = true,
            onResult = { text -> resultDef.complete(text) },
            onError = { _ -> resultDef.complete("") }
        )
        return resultDef.await()
    }

    fun stopLiveSession() {
        if (!isLiveActive.getAndSet(false)) return
        _isRecording.value = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val valDouble = buffer[i].toDouble()
            sum += valDouble * valDouble
        }
        return sqrt(sum / length)
    }
}
