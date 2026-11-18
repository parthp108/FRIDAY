package com.health.friday.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import com.health.friday.os.inference.InferenceDaemonService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LocalInferenceBridge(private val context: Context) {

    companion object {
        private const val TAG = "LocalInferenceBridge"
    }

    private var daemonBinder: IInferenceDaemon? = null
    private var isBound = false
    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, ":npu_inference process died under memory pressure. Re-binding automatically...")
        daemonBinder = null
        isBound = false
        bindDaemonService()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                service?.linkToDeath(deathRecipient, 0)
                daemonBinder = IInferenceDaemon.Stub.asInterface(service)
                isBound = true
                Log.i(TAG, "Successfully bound to :npu_inference process daemon")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link death recipient to :npu_inference daemon", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, ":npu_inference service disconnected")
            daemonBinder = null
            isBound = false
        }
    }

    fun bindDaemonService() {
        if (isBound) return
        try {
            val intent = Intent(context, InferenceDaemonService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind to InferenceDaemonService", e)
        }
    }

    suspend fun initialize(modelPath: String?, systemPromptPrefix: String): Boolean = withContext(Dispatchers.IO) {
        bindDaemonService()
        var retries = 0
        while (daemonBinder == null && retries < 20) {
            delay(100)
            retries++
        }
        val binder = daemonBinder ?: return@withContext false
        try {
            binder.initializeEngine(modelPath, systemPromptPrefix)
        } catch (e: Exception) {
            Log.e(TAG, "IPC error initializing NPU engine", e)
            false
        }
    }

    suspend fun generateStream(
        userPrompt: String,
        screenBitmap: Bitmap? = null,
        onToken: (String) -> Unit,
        onComplete: (fullText: String, ttftMs: Long, tokensPerSec: Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        bindDaemonService()
        val binder = daemonBinder
        if (binder == null) {
            onError("NPU inference daemon not connected.")
            return@withContext
        }

        val deferred = CompletableDeferred<Unit>()

        val callback = object : IInferenceCallback.Stub() {
            override fun onPartialToken(token: String?) {
                token?.let { onToken(it) }
            }

            override fun onComplete(fullText: String?, ttftMs: Long, tokensPerSec: Float) {
                onComplete(fullText.orEmpty(), ttftMs, tokensPerSec)
                deferred.complete(Unit)
            }

            override fun onError(errorMessage: String?) {
                onError(errorMessage ?: "Unknown IPC inference error")
                deferred.complete(Unit)
            }
        }

        try {
            if (screenBitmap != null) {
                binder.generateVisionStream(userPrompt, screenBitmap, callback)
            } else {
                binder.generateStream(userPrompt, callback)
            }
            deferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "IPC error during streaming generation", e)
            onError(e.message ?: "IPC communication failure")
        }
    }

    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from InferenceDaemonService", e)
            }
            isBound = false
            daemonBinder = null
        }
    }
}
