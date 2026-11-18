package com.health.friday.desktop.bridge

import com.health.friday.shared.models.*
import com.health.friday.shared.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.min

class DesktopSyncClient(private val port: Int = 8088) {

    companion object {
        // The phone listens on ITS OWN loopback; `adb forward` bridges it over USB.
        private const val HOST = "127.0.0.1"
        private const val MAX_LINE_CHARS = 4 * 1024 * 1024
        private const val ADB_RETRY_MS = 5_000L
        private const val MAX_BACKOFF_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Only the connection loop touches the clock (HybridLogicalClock is not thread-safe).
    private val clock = HybridLogicalClock(DesktopIdentity.deviceId, System::currentTimeMillis)

    @Volatile private var socket: Socket? = null
    @Volatile private var wantConnected = false
    private var loopJob: Job? = null
    private var lastAdbAttemptMs = 0L
    private var adbWarned = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    /** Device name from the phone's hello, e.g. "samsung SM-S911B". */
    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName

    fun connect(
        onMealsReceived: (List<FridayMealDto>) -> Unit,
        onTasksReceived: (List<FridayTaskDto>) -> Unit,
        onRemindersReceived: (List<FridayReminderDto>) -> Unit,
        onSignalReceived: (DailySignalDto) -> Unit,
        onRejected: (String) -> Unit = {}
    ) {
        if (wantConnected) return
        wantConnected = true

        loopJob = scope.launch {
            var backoffMs = 1_000L
            while (wantConnected) {
                ensureAdbForward()
                val s = openSocket()
                if (s == null) {
                    _isConnected.value = false
                    delay(backoffMs)
                    backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                    continue
                }

                backoffMs = 1_000L
                socket = s
                try {
                    session(s, onMealsReceived, onTasksReceived, onRemindersReceived, onSignalReceived, onRejected)
                } catch (_: Exception) {
                    // connection dropped: fall through to reconnect
                } finally {
                    _isConnected.value = false
                    _peerName.value = null
                    runCatching { s.close() }
                    socket = null
                }
                if (wantConnected) delay(1_000L)
            }
        }
    }

    private fun openSocket(): Socket? {
        val s = Socket()
        return try {
            s.connect(InetSocketAddress(HOST, port), 1_500)
            s
        } catch (_: Exception) {
            runCatching { s.close() }
            null
        }
    }

    private fun send(writer: PrintWriter, payload: Payload, replyTo: String? = null) {
        writer.println(ProtocolCodec.encode(createEnvelope(clock, payload, replyTo)))
    }

    private fun session(
        s: Socket,
        onMeals: (List<FridayMealDto>) -> Unit,
        onTasks: (List<FridayTaskDto>) -> Unit,
        onReminders: (List<FridayReminderDto>) -> Unit,
        onSignal: (DailySignalDto) -> Unit,
        onRejected: (String) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)

        send(
            writer,
            Hello(
                deviceId = DesktopIdentity.deviceId,
                deviceName = DesktopIdentity.deviceName,
                role = DeviceRole.DESKTOP,
                appVersion = "0.1",
                capabilities = emptyList()
            )
        )

        while (wantConnected) {
            val line = readBoundedLine(reader) ?: return
            when (val decoded = ProtocolCodec.decode(line)) {
                is Decoded.Ok -> {
                    val env = decoded.envelope
                    HlcTimestamp.parse(env.ts)?.let { runCatching { clock.receive(it) } }

                    // "Connected" only once the PHONE answers: adb accepts the TCP connection
                    // even when nothing is listening on the phone.
                    _isConnected.value = true

                    when (val p = env.payload) {
                        is Hello -> _peerName.value = p.deviceName
                        is MealsSnapshot -> onMeals(p.meals)
                        is TasksSnapshot -> onTasks(p.tasks)
                        is RemindersSnapshot -> onReminders(p.reminders)
                        is SignalsSnapshot -> onSignal(p.signals)
                        is ErrorReply -> onRejected("${p.code}: ${p.message}")
                        is Ping -> send(writer, Pong, env.id)
                        else -> Unit
                    }
                }
                is Decoded.VersionMismatch -> {
                    onRejected("The phone uses a different sync protocol version. Update both apps.")
                    return
                }
                is Decoded.Unsupported -> System.err.println("[sync] ignoring unknown message '${decoded.payloadType}'")
                is Decoded.Malformed -> System.err.println("[sync] malformed message: ${decoded.reason}")
            }
        }
    }

    private fun ensureAdbForward() {
        val now = System.currentTimeMillis()
        if (now - lastAdbAttemptMs < ADB_RETRY_MS) return
        lastAdbAttemptMs = now
        try {
            val p = ProcessBuilder("adb", "forward", "tcp:$port", "tcp:$port")
                .redirectErrorStream(true)
                .start()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return
            }
            if (p.exitValue() != 0 && !adbWarned) {
                adbWarned = true
                System.err.println("[sync] adb forward failed: " + p.inputStream.bufferedReader().readText().take(200))
            }
        } catch (e: IOException) {
            if (!adbWarned) {
                adbWarned = true
                System.err.println("[sync] could not run adb (is it on PATH?): ${e.message}")
            }
        }
    }

    private fun readBoundedLine(reader: BufferedReader): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > MAX_LINE_CHARS) throw IOException("Line too long")
        }
    }

    fun disconnect() {
        wantConnected = false
        _isConnected.value = false
        runCatching { socket?.close() }
        loopJob?.cancel()
    }
}
