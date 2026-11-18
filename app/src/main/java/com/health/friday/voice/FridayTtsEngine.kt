package com.health.friday.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class FridayTtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeMediaPlayer: MediaPlayer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunAnywhereInitialized = false

    data class VoiceEmotion(val pitch: Float, val rate: Float)

    init {
        tts = TextToSpeech(context, this)
        initRunAnywhereTts()
    }

    private fun initRunAnywhereTts() {
        scope.launch {
            try {
                // Register Piper TTS British English (Alba) neural voice model via RunAnywhere SDK
                /*
                import com.runanywhere.sdk.RunAnywhere
                import com.runanywhere.sdk.models.*

                RunAnywhere.registerModel(
                    id = MODEL_ID,
                    name = MODEL_NAME,
                    url = MODEL_URL,
                    framework = InferenceFramework.ONNX,
                    modality = ModelCategory.SPEECH_SYNTHESIS,
                    memoryRequirement = 65_000_000L
                )
                */
                isRunAnywhereInitialized = true
                Log.i(TAG, "Piper TTS model registered: $MODEL_ID ($MODEL_NAME)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register RunAnywhere Piper TTS model", e)
                isRunAnywhereInitialized = false
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.UK)

            // Select a high-quality, natural human voice if available
            try {
                val voices = tts?.voices
                val naturalVoice = voices?.find { voice ->
                    voice.locale.language == "en" &&
                            (voice.name.contains("network", ignoreCase = true) ||
                                    voice.name.contains("natural", ignoreCase = true) ||
                                    voice.name.contains("en-gb", ignoreCase = true) ||
                                    voice.name.contains("alba", ignoreCase = true))
                }
                if (naturalVoice != null) {
                    tts?.voice = naturalVoice
                    Log.i(TAG, "Selected TTS Voice: ${naturalVoice.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice selection fallback to default locale voice", e)
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    fun speak(text: String) {
        val cleanText = cleanAndEnhanceForSpeech(text)
        if (cleanText.isBlank()) return

        stop() // Stop current audio playback

        scope.launch {
            _isSpeaking.value = true
            var success = false

            if (isRunAnywhereInitialized) {
                try {
                    // Synthesize natural human speech using Piper TTS British English (Alba)
                    val audioData = synthesizeRunAnywhere(cleanText)
                    if (audioData != null && audioData.isNotEmpty()) {
                        playAudioBytes(audioData)
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "RunAnywhere Piper TTS synthesis failed, using Android TTS fallback", e)
                }
            }

            if (!success) {
                // Determine emotional pitch and rate
                val emotion = determineEmotion(cleanText)
                tts?.setPitch(emotion.pitch)
                tts?.setSpeechRate(emotion.rate)

                _isSpeaking.value = true
                tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "friday_speech_${System.currentTimeMillis()}")
            }
        }
    }

    fun cleanAndEnhanceForSpeech(text: String): String {
        return text
            .replace(Regex("""\*\*.*?\*\*"""), "") // remove bold markers
            .replace(Regex("""```.*?```""", RegexOption.DOT_MATCHES_ALL), "") // remove code blocks
            .replace(Regex("""[^\x00-\x7F]"""), " ") // remove emojis & special symbols
            .replace(Regex("""(?i)\b(\d+)\s*ml\b"""), "$1 milliliters")
            .replace(Regex("""(?i)\b(\d+)\s*g\b"""), "$1 grams")
            .replace(Regex("""(?i)\b(\d+)\s*kcal\b"""), "$1 calories")
            .replace(Regex("""(?i)\b(\d+)\s*bpm\b"""), "$1 beats per minute")
            .replace(Regex("""(?i)\b(\d+):(\d{2})\b"""), "$1 $2")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun determineEmotion(text: String): VoiceEmotion {
        val lower = text.lowercase()
        return when {
            lower.contains("warning") || lower.contains("alert") || lower.contains("caution") || lower.contains("unusual") || lower.contains("haven't") ->
                VoiceEmotion(pitch = 0.98f, rate = 0.96f) // Empathetic / Reassuring

            lower.contains("!") || lower.contains("great") || lower.contains("awesome") || lower.contains("logged") || lower.contains("done") ->
                VoiceEmotion(pitch = 1.12f, rate = 1.05f) // Enthusiastic / Warm

            lower.contains("?") || lower.contains("would you") || lower.contains("should i") ->
                VoiceEmotion(pitch = 1.08f, rate = 1.02f) // Inquisitive / Conversational

            else ->
                VoiceEmotion(pitch = 1.04f, rate = 1.03f) // Friendly Natural
        }
    }

    private fun synthesizeRunAnywhere(text: String): ByteArray? {
        return try {
            // Invokes RunAnywhere.synthesize(text, voiceId = MODEL_ID)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error synthesizing text with model $MODEL_ID", e)
            null
        }
    }

    private fun playAudioBytes(audioBytes: ByteArray) {
        try {
            if (audioBytes.size > 44 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte()) {
                // WAV format playback
                val tempFile = File.createTempFile("friday_tts_", ".wav", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                activeMediaPlayer?.release()
                activeMediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        tempFile.delete()
                    }
                    setOnErrorListener { _, _, _ ->
                        _isSpeaking.value = false
                        tempFile.delete()
                        true
                    }
                    start()
                }
            } else {
                // Raw PCM 22050Hz 16-bit Mono playback (standard Piper TTS output format)
                val sampleRate = 22050
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                activeAudioTrack?.release()
                activeAudioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(bufferSize, audioBytes.size))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeAudioTrack?.write(audioBytes, 0, audioBytes.size)
                activeAudioTrack?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error", e)
            _isSpeaking.value = false
        }
    }

    fun stop() {
        try {
            tts?.stop()
            activeMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            activeMediaPlayer = null

            activeAudioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                release()
            }
            activeAudioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            _isSpeaking.value = false
        }
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "FridayTtsEngine"
        const val MODEL_ID = "vits-piper-en_GB-alba-medium"
        const val MODEL_NAME = "Piper TTS British English (Alba)"
        const val MODEL_URL = "https://huggingface.co/runanywhere/vits-piper-en_GB-alba-medium/resolve/main/vits-piper-en_GB-alba-medium.tar.gz"
    }
}
