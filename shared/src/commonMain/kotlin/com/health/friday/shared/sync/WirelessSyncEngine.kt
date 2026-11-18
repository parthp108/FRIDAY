package com.health.friday.shared.sync

import com.health.friday.shared.models.*
import com.health.friday.shared.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Multiplatform Wireless Sync Engine supporting:
 * 1. USB Loopback Sync (127.0.0.1:8088)
 * 2. Local Wi-Fi LAN Sync (ws://<desktop-ip>:8089/ws/friday)
 * 3. Over-the-Internet Cloud Relay Sync (wss://<cloud-relay-url>/ws/relay)
 */
class WirelessSyncEngine(
    private val client: HttpClient = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 15000
        }
    }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    @Volatile private var activeSession: DefaultClientWebSocketSession? = null

    private val _syncState = MutableStateFlow<SyncConnectionState>(SyncConnectionState.Disconnected)
    val syncState: StateFlow<SyncConnectionState> = _syncState

    private val _connectedPeerName = MutableStateFlow<String?>(null)
    val connectedPeerName: StateFlow<String?> = _connectedPeerName

    sealed interface SyncConnectionState {
        data object Disconnected : SyncConnectionState
        data class Connecting(val endpoint: String) : SyncConnectionState
        data class Connected(val mode: SyncMode, val endpoint: String) : SyncConnectionState
        data class Error(val message: String) : SyncConnectionState
    }

    enum class SyncMode { USB, WIFI_LAN, INTERNET_CLOUD }

    fun startWirelessSync(
        targetHost: String = "127.0.0.1",
        targetPort: Int = 8089,
        cloudRelayUrl: String? = null,
        pairingCode: String = "FRIDAY-ECO-LIVE",
        onEnvelopeReceived: (Envelope) -> Unit = {}
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                // 1. Try local Wi-Fi / USB host first
                val lanConnected = tryConnectWebSocket(
                    host = targetHost,
                    port = targetPort,
                    path = "/ws/friday",
                    ssl = false,
                    mode = if (targetHost == "127.0.0.1") SyncMode.USB else SyncMode.WIFI_LAN,
                    onEnvelopeReceived = onEnvelopeReceived
                )

                if (lanConnected) {
                    delay(3000)
                    continue
                }

                // 2. Fallback to Over-the-Internet Cloud Relay if Wi-Fi / USB is unavailable
                val relayHost = cloudRelayUrl ?: "relay.friday-eco.org"
                val cloudConnected = tryConnectWebSocket(
                    host = relayHost,
                    port = 443,
                    path = "/ws/relay?pairCode=$pairingCode",
                    ssl = true,
                    mode = SyncMode.INTERNET_CLOUD,
                    onEnvelopeReceived = onEnvelopeReceived
                )

                if (!cloudConnected) {
                    _syncState.value = SyncConnectionState.Disconnected
                    delay(5000) // Wait 5 seconds before retrying discovery
                }
            }
        }
    }

    private suspend fun tryConnectWebSocket(
        host: String,
        port: Int,
        path: String,
        ssl: Boolean,
        mode: SyncMode,
        onEnvelopeReceived: (Envelope) -> Unit
    ): Boolean {
        val protocol = if (ssl) "wss" else "ws"
        val endpoint = "$protocol://$host:$port$path"
        _syncState.value = SyncConnectionState.Connecting(endpoint)

        return try {
            client.webSocket(
                method = HttpMethod.Get,
                host = host,
                port = port,
                path = path
            ) {
                activeSession = this
                _syncState.value = SyncConnectionState.Connected(mode, endpoint)

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        runCatching {
                            val envelope = Json.decodeFromString<Envelope>(text)
                            if (envelope.payload is Hello) {
                                _connectedPeerName.value = (envelope.payload as Hello).deviceName
                            }
                            onEnvelopeReceived(envelope)
                        }
                    }
                }
                activeSession = null
                false
            }
            true
        } catch (_: Exception) {
            activeSession = null
            false
        }
    }

    fun sendPayload(payload: Payload, clockNodeId: String = "client") {
        val session = activeSession ?: return
        scope.launch {
            try {
                val envelope = Envelope(
                    id = "msg_${System.currentTimeMillis()}",
                    from = clockNodeId,
                    ts = System.currentTimeMillis().toString(),
                    payload = payload
                )
                val text = Json.encodeToString(envelope)
                session.send(Frame.Text(text))
            } catch (_: Exception) {}
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        activeSession = null
        _syncState.value = SyncConnectionState.Disconnected
    }
}
