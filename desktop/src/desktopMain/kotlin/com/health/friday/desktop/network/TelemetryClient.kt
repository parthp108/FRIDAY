package com.health.friday.desktop.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

class TelemetryClient(private val deviceIp: String) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 1.seconds
        }
    }

    // Holds the raw, zero-copy semantic screenshot bytes
    private val _screenPixels = MutableStateFlow<ByteArray?>(null)
    val screenPixels: StateFlow<ByteArray?> = _screenPixels

    // Holds network/frame latency statistics for the chart
    private val _telemetryStats = MutableStateFlow<List<Float>>(List(50) { 0f })
    val telemetryStats: StateFlow<List<Float>> = _telemetryStats

    private var currentQualityProfile: Int = 100 // 100% quality

    suspend fun connect() {
        try {
            // Android IPC layer acts as the server on port 8080
            client.webSocket(
                method = io.ktor.http.HttpMethod.Get,
                host = deviceIp,
                port = 8080,
                path = "/friday-stream"
            ) {
                while (isActive) {
                    val receiveTime = System.currentTimeMillis()
                    when (val frame = incoming.receive()) {
                        is Frame.Binary -> {
                            _screenPixels.value = frame.readBytes()
                            val renderLatency = System.currentTimeMillis() - receiveTime
                            updateStats(renderLatency.toFloat())
                            adjustStreamQuality(this, renderLatency)
                        }
                        is Frame.Text -> {
                            val latency = frame.readText().toFloatOrNull()
                            if (latency != null) {
                                updateStats(latency)
                            }
                        }
                        else -> {}
                    }
                }
            }
        } catch (_: Exception) {
            // Handle disconnection / retry logic
        }
    }

    private suspend fun adjustStreamQuality(session: DefaultClientWebSocketSession, latency: Long) {
        if (latency > 16 && currentQualityProfile > 50) {
            currentQualityProfile -= 10
            session.send(Frame.Text("CMD_SET_QUALITY:$currentQualityProfile"))
        } else if (latency < 10 && currentQualityProfile < 100) {
            currentQualityProfile += 5
            session.send(Frame.Text("CMD_SET_QUALITY:$currentQualityProfile"))
        }
    }

    private fun updateStats(newVal: Float) {
        val current = _telemetryStats.value.toMutableList()
        if (current.size >= 50) current.removeAt(0)
        current.add(newVal)
        _telemetryStats.value = current
    }

    fun close() {
        client.close()
    }
}
