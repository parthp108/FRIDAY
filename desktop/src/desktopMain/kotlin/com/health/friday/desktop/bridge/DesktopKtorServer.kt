package com.health.friday.desktop.bridge

import com.health.friday.shared.protocol.Envelope
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class DesktopKtorServer(private val port: Int = 8089) {

    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeSessions = ConcurrentHashMap.newKeySet<Any>()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients

    fun start(onEnvelopeReceived: (Envelope) -> Unit = {}) {
        if (server != null) return

        scope.launch {
            try {
                val s = embeddedServer(CIO, port = port) {
                    install(WebSockets) {
                        pingPeriod = 15.seconds
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }

                    routing {
                        webSocket("/ws/friday") {
                            val session = this
                            activeSessions.add(session)
                            _connectedClients.value = activeSessions.size
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        try {
                                            val envelope = Json.decodeFromString<Envelope>(text)
                                            onEnvelopeReceived(envelope)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                activeSessions.remove(session)
                                _connectedClients.value = activeSessions.size
                            }
                        }
                    }
                }
                server = s
                s.start(wait = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun broadcast(envelope: Envelope) {
        scope.launch {
            val jsonText = Json.encodeToString(envelope)
            for (session in activeSessions) {
                try {
                    (session as? io.ktor.websocket.WebSocketSession)?.send(Frame.Text(jsonText))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        activeSessions.clear()
        _connectedClients.value = 0
    }
}
