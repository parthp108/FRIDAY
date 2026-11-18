package com.health.friday.sovereign

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SovereignHomeWebSocketClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "FRIDAY_HOME_BUS"
    }

    private var webSocket: WebSocket? = null
    private val isAuthenticated = AtomicBoolean(false)
    private val messageIdCounter = AtomicInteger(1)
    private var accessToken: String = ""

    /**
     * Connects to the local Home Assistant / Matter controller WebSocket on the LAN.
     */
    fun connectLocalHub(hostIp: String, port: Int = 8123, longLivedAccessToken: String) {
        accessToken = longLivedAccessToken
        val url = "ws://$hostIp:$port/api/websocket"
        val request = Request.Builder().url(url).build()

        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(request, socketListener)
    }

    fun isConnectedAndAuthenticated(): Boolean = isAuthenticated.get()

    /**
     * Executes a deterministic smart-home service call over the local WebSocket bus.
     */
    fun callHomeService(
        domain: String,
        service: String,
        entityId: String,
        serviceData: Map<String, Any> = emptyMap()
    ): Boolean {
        val ws = webSocket ?: return false
        if (!isAuthenticated.get()) return false

        val dataJson = JSONObject().apply {
            put("entity_id", entityId)
            for ((k, v) in serviceData) {
                put(k, v)
            }
        }

        val payload = JSONObject().apply {
            put("id", messageIdCounter.getAndIncrement())
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            put("service_data", dataJson)
        }

        return ws.send(payload.toString())
    }

    /**
     * Autonomous Migraine / High-Stress Room Adaptation.
     */
    fun engageRoomMigraineReliefMode(
        lightEntityId: String = "light.living_room",
        speakerEntityId: String = "media_player.living_room_speaker"
    ) {
        callHomeService(
            domain = "light",
            service = "turn_on",
            entityId = lightEntityId,
            serviceData = mapOf(
                "brightness_pct" to 15,
                "kelvin" to 2200,
                "transition" to 3
            )
        )
        callHomeService(
            domain = "media_player",
            service = "volume_set",
            entityId = speakerEntityId,
            serviceData = mapOf("volume_level" to 0.20)
        )
        Log.i(TAG, "Dispatched autonomous room migraine/stress relief scene over local LAN.")
    }

    private val socketListener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "auth_required" -> {
                        val authMsg = JSONObject().apply {
                            put("type", "auth")
                            put("access_token", accessToken)
                        }
                        webSocket.send(authMsg.toString())
                    }
                    "auth_ok" -> {
                        isAuthenticated.set(true)
                        Log.i(TAG, "Authenticated with local Sovereign Home Hub.")
                    }
                    "auth_invalid" -> {
                        isAuthenticated.set(false)
                        Log.e(TAG, "Local Home Hub authentication rejected: ${json.optString("message")}")
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isAuthenticated.set(false)
            Log.w(TAG, "Local Home WebSocket disconnected: ${t.message}")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isAuthenticated.set(false)
        }
    }

    fun disconnect() {
        isAuthenticated.set(false)
        webSocket?.close(1000, "Service shutdown")
        webSocket = null
    }
}
