package com.health.friday.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class FridaySpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "FRIDAY_SPEECH"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (speechRecognizer == null) {
            runCatching {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
        return speechRecognizer
    }

    fun startListening(
        isLongForm: Boolean = false,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission not granted.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service unavailable on this device.")
            return
        }

        mainHandler.post {
            try {
                _isListening.value = true
                _partialResults.value = ""

                val recognizer = getOrCreateRecognizer()
                if (recognizer == null) {
                    _isListening.value = false
                    onError("Failed to create SpeechRecognizer instance.")
                    return@post
                }

                runCatching { recognizer.cancel() }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        Log.d(TAG, "Ready for speech input.")
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        Log.d(TAG, "Speech started.")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDb.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _rmsDb.value = 0f
                        Log.d(TAG, "Speech ended.")
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsDb.value = 0f

                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Error from server"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            else -> "Speech recognition error ($error)"
                        }

                        Log.w(TAG, "Speech error code $error: $errorMessage")

                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                            runCatching {
                                speechRecognizer?.destroy()
                                speechRecognizer = null
                            }
                        }

                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onError(errorMessage)
                        } else {
                            onResult("")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsDb.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull()?.trim() ?: ""
                        Log.i(TAG, "Recognized speech: '$bestMatch'")
                        onResult(bestMatch)
                    }

                    override fun onPartialResults(partial: Bundle?) {
                        val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val match = matches?.firstOrNull()?.trim() ?: ""
                        if (match.isNotBlank()) {
                            _partialResults.value = match
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                    val silenceTimeoutMs = if (isLongForm) 15000L else 8000L
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (isLongForm) 8000L else 3000L)
                }

                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                _isListening.value = false
                Log.e(TAG, "Failed to start speech recognizer", e)
                onError("Failed to start speech recognizer: ${e.message}")
            }
        }
    }

    private var isContinuousSessionActive = false

    fun startContinuousSession(
        onSegmentRecognized: (String) -> Unit,
        onPartialText: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission not granted.")
            return
        }

        isContinuousSessionActive = true
        _isListening.value = true

        mainHandler.post {
            try {
                val recognizer = getOrCreateRecognizer() ?: return@post
                _partialResults.value = ""

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDb.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (!isContinuousSessionActive) {
                            _isListening.value = false
                            _rmsDb.value = 0f
                        }
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "Continuous session quiet/error code: $error")
                        if (isContinuousSessionActive) {
                            _isListening.value = true
                            mainHandler.postDelayed({
                                runCatching { recognizer.startListening(intent) }
                            }, 150)
                        } else {
                            _isListening.value = false
                            _rmsDb.value = 0f
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            onSegmentRecognized(text)
                        }
                        if (isContinuousSessionActive) {
                            _isListening.value = true
                            mainHandler.postDelayed({
                                runCatching { recognizer.startListening(intent) }
                            }, 150)
                        } else {
                            _isListening.value = false
                            _rmsDb.value = 0f
                        }
                    }

                    override fun onPartialResults(partial: Bundle?) {
                        val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            _partialResults.value = text
                            onPartialText(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)

            } catch (e: Exception) {
                Log.e(TAG, "Continuous session error", e)
                onError("Failed to start continuous session: ${e.message}")
            }
        }
    }

    fun stopContinuousSession() {
        isContinuousSessionActive = false
        stopListening()
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    fun destroy() {
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }
}
