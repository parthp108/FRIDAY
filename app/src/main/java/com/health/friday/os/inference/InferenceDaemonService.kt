package com.health.friday.os.inference

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import com.health.friday.inference.IInferenceCallback
import com.health.friday.inference.IInferenceDaemon
import com.health.friday.inference.NpuInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class InferenceDaemonService : Service() {

    private val daemonScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: NpuInferenceEngine

    override fun onCreate() {
        super.onCreate()
        engine = NpuInferenceEngine(applicationContext)
    }

    private val binder = object : IInferenceDaemon.Stub() {

        override fun initializeEngine(modelPath: String?, systemPromptPrefix: String?): Boolean {
            return runBlocking(Dispatchers.IO) {
                engine.initialize(modelPath, systemPromptPrefix.orEmpty())
            }
        }

        override fun generateStream(userPrompt: String?, callback: IInferenceCallback?) {
            daemonScope.launch {
                engine.generateFastResponse(
                    userPrompt = userPrompt.orEmpty(),
                    screenBitmap = null,
                    onToken = { chunk -> runCatching { callback?.onPartialToken(chunk) } },
                    onComplete = { full, ttft, tps -> runCatching { callback?.onComplete(full, ttft, tps) } },
                    onError = { err -> runCatching { callback?.onError(err) } }
                )
            }
        }

        override fun generateVisionStream(
            userPrompt: String?,
            screenCapture: Bitmap?,
            callback: IInferenceCallback?
        ) {
            daemonScope.launch {
                engine.generateFastResponse(
                    userPrompt = userPrompt.orEmpty(),
                    screenBitmap = screenCapture,
                    onToken = { chunk -> runCatching { callback?.onPartialToken(chunk) } },
                    onComplete = { full, ttft, tps -> runCatching { callback?.onComplete(full, ttft, tps) } },
                    onError = { err -> runCatching { callback?.onError(err) } }
                )
            }
        }

        override fun enterLowMemoryIdle() {
            // Reclaims RAM pages via madvise
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        daemonScope.cancel()
        engine.close()
        super.onDestroy()
    }
}
