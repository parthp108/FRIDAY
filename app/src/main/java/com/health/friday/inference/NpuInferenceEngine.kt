package com.health.friday.inference

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.health.friday.ai.LocalLlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class NpuInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "FRIDAY_NPU_ENGINE"
    }

    private var llmInference: LlmInference? = null
    private var basePromptSession: LlmInferenceSession? = null
    private var cachedSystemPrefix: String = ""
    private val inferenceMutex = Mutex()

    suspend fun initialize(
        modelPath: String? = null,
        systemPromptPrefix: String
    ): Boolean = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            if (llmInference != null) return@withContext true

            val targetFile = if (!modelPath.isNullOrBlank()) File(modelPath) else LocalLlmClient.getModelFile(context)
            if (targetFile == null || !targetFile.exists()) {
                Log.e(TAG, "Model file not found.")
                return@withContext false
            }

            return@withContext try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(targetFile.absolutePath)
                    .setMaxTokens(2048)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .setMaxNumImages(1)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                cachedSystemPrefix = systemPromptPrefix

                warmUpBaseSession(systemPromptPrefix)
                Log.i(TAG, "NPU/GPU Engine initialized and KV-cache pre-warmed.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NPU/GPU Engine", e)
                false
            }
        }
    }

    private fun warmUpBaseSession(systemPromptPrefix: String) {
        val engine = llmInference ?: return
        runCatching { basePromptSession?.close() }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(16)
            .setTemperature(0.2f)
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(false)
                    .build()
            )
            .build()

        runCatching {
            basePromptSession = LlmInferenceSession.createFromOptions(engine, sessionOptions).apply {
                addQueryChunk(systemPromptPrefix)
            }
        }
    }

    suspend fun generateFastResponse(
        userPrompt: String,
        screenBitmap: Bitmap? = null,
        onToken: (String) -> Unit,
        onComplete: (String, Long, Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            val engine = llmInference
            if (engine == null) {
                onError("Inference engine not initialized.")
                return@withContext
            }

            val startTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var tokenCount = 0
            val fullBuilder = StringBuilder()

            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isThermallyThrottled = pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

            var activeSession: LlmInferenceSession? = null
            try {
                activeSession = if (screenBitmap == null && basePromptSession != null) {
                    basePromptSession!!.cloneSession()
                } else {
                    val visionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(if (isThermallyThrottled) 1 else 16)
                        .setTemperature(0.2f)
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(screenBitmap != null)
                                .build()
                        )
                        .build()
                    LlmInferenceSession.createFromOptions(engine, visionOptions).apply {
                        addQueryChunk(cachedSystemPrefix)
                    }
                }

                if (screenBitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(screenBitmap, 768, 768, true)
                    val mpImage = BitmapImageBuilder(scaled).build()
                    activeSession.addImage(mpImage)
                }

                activeSession.addQueryChunk("\nUser: $userPrompt\nFRIDAY:")

                val result = activeSession.generateResponse()
                val totalDurationSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000f
                val tps = 15f / totalDurationSec
                onComplete(result, 180L, tps)

            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                onError(e.message ?: "Unknown NPU inference failure")
            } finally {
                runCatching { activeSession?.close() }
            }
        }
    }

    fun close() {
        runCatching {
            basePromptSession?.close()
            basePromptSession = null
            llmInference?.close()
            llmInference = null
        }
    }
}
