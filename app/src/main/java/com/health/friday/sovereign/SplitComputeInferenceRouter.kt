package com.health.friday.sovereign

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.health.friday.inference.NpuInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class SplitComputeInferenceRouter(
    private val localNpuEngine: NpuInferenceEngine,
    private val discoveryManager: LanMeshDiscoveryManager,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "FRIDAY_SPLIT_COMPUTE"
        private const val NPU_PREFILL_TPS = 180.0
        private const val NPU_DECODE_TPS = 18.0
        private const val LAN_GPU_PREFILL_TPS = 1800.0
        private const val LAN_GPU_DECODE_TPS = 75.0
        private const val ASSUMED_LAN_BANDWIDTH_BYTES_PER_SEC = 15_000_000.0
    }

    var preferredLanModelName: String = "qwen2.5-vl:14b"

    /**
     * Evaluates the latency cost inequality and routes inference either to a discovered
     * local LAN GPU server (vLLM / llama-server / Ollama) or the on-device Snapdragon NPU.
     */
    suspend fun generateAdaptiveResponse(
        userPrompt: String,
        screenBitmap: Bitmap? = null,
        expectedOutputTokens: Int = 128,
        onToken: (String) -> Unit = {},
        onComplete: (String, String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val lanServer = discoveryManager.discoveredNodes.value
            .firstOrNull { it.nodeType == MeshNodeType.LOCAL_LLM_SERVER }

        if (lanServer != null) {
            val rttMs = measureTcpRttMs(lanServer.hostAddress, lanServer.port)
            val promptTokens = (userPrompt.length / 4).coerceAtLeast(16)
            val imgBytes = if (screenBitmap != null) 180_000 else 0

            val tLocalSec = (if (screenBitmap != null) 0.65 else 0.0) +
                    (promptTokens / NPU_PREFILL_TPS) +
                    (expectedOutputTokens / NPU_DECODE_TPS)

            val tLanSec = (rttMs / 1000.0) +
                    ((imgBytes + promptTokens * 4) / ASSUMED_LAN_BANDWIDTH_BYTES_PER_SEC) +
                    (promptTokens / LAN_GPU_PREFILL_TPS) +
                    (expectedOutputTokens / LAN_GPU_DECODE_TPS)

            if (rttMs in 1..15 && tLanSec <= 0.65 * tLocalSec) {
                Log.i(
                    TAG,
                    "Offloading to Local LAN GPU (${lanServer.hostAddress}:${lanServer.port}, " +
                            "RTT=${rttMs}ms, T_LAN=${"%.2f".format(tLanSec)}s vs T_NPU=${"%.2f".format(tLocalSec)}s)"
                )
                val lanReply = executeLanGpuInference(lanServer, userPrompt, screenBitmap)
                if (lanReply != null) {
                    onToken(lanReply)
                    onComplete(lanReply, "LAN_GPU_${lanServer.hostAddress}")
                    return@withContext
                }
            }
        }

        localNpuEngine.generateFastResponse(
            userPrompt = userPrompt,
            screenBitmap = screenBitmap,
            onToken = onToken,
            onComplete = { fullText, _, _ ->
                onComplete(fullText, "ON_DEVICE_SNAPDRAGON_NPU")
            },
            onError = onError
        )
    }

    private fun measureTcpRttMs(host: String, port: Int): Long {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 250)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (e: Exception) {
            -1L
        }
    }

    private fun executeLanGpuInference(
        server: DiscoveredMeshNode,
        prompt: String,
        bitmap: Bitmap?
    ): String? {
        return try {
            val contentArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                if (bitmap != null) {
                    val b64 = encodeBitmapBase64Jpeg(bitmap)
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                }
            }

            val requestJson = JSONObject().apply {
                put("model", preferredLanModelName)
                put("temperature", 0.2)
                put("stream", false)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", contentArray)
                    }
                ))
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://${server.hostAddress}:${server.port}/v1/chat/completions")
                .post(body)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val respBody = resp.body?.string() ?: return null
                val root = JSONObject(respBody)
                root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "LAN GPU request failed, falling back to on-device NPU: ${e.message}")
            null
        }
    }

    private fun encodeBitmapBase64Jpeg(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 1024, 1024, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
