package com.health.friday.bridge

import android.content.Context
import android.util.Log
import com.health.friday.BuildConfig
import com.health.friday.data.local.*
import com.health.friday.shared.models.*
import com.health.friday.shared.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArraySet

class LocalSyncServer(
    private val context: Context,
    private val port: Int = 8088
) {
    companion object {
        private const val TAG = "FRIDAY_SYNC"
        private const val MAX_CLIENTS = 5
        private const val MAX_LINE_CHARS = 64 * 1024
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val IDLE_TIMEOUT_MS = 5 * 60_000
        private const val MAX_MEALS_SENT = 500
        private const val MAX_MALFORMED = 5
    }

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private var scope: CoroutineScope? = null
    private val clients = CopyOnWriteArraySet<Socket>()

    private val db by lazy { FridayDatabase.getDatabase(context) }
    private val identity by lazy { DeviceIdentity(context) }
    private val clock by lazy { HybridLogicalClock(identity.deviceId, System::currentTimeMillis) }

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients

    @Synchronized
    fun startServer(onMessageReceived: (Envelope) -> Unit = {}): Boolean {
        if (isRunning) return true

        val socket = try {
            ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            Log.w(TAG, "Sync server not started: ${e.message}")
            return false
        }

        serverSocket = socket
        isRunning = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s

        s.launch {
            Log.i(TAG, "Sync server listening on 127.0.0.1:$port (protocol v$PROTOCOL_VERSION)")
            while (isRunning) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                if (clients.size >= MAX_CLIENTS) {
                    runCatching { client.close() }
                    continue
                }
                clients.add(client)
                _connectedClients.update { it + 1 }
                handleClient(s, client, onMessageReceived)
            }
        }
        return true
    }

    private fun frame(payload: Payload, replyTo: String? = null): String =
        synchronized(clock) { ProtocolCodec.encode(createEnvelope(clock, payload, replyTo)) }

    private fun handleClient(s: CoroutineScope, socket: Socket, onMessage: (Envelope) -> Unit) {
        s.launch {
            var handshaken = false
            var malformed = 0
            try {
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

                writer.println(
                    frame(
                        Hello(
                            deviceId = identity.deviceId,
                            deviceName = identity.deviceName,
                            role = DeviceRole.PHONE,
                            appVersion = BuildConfig.VERSION_NAME,
                            capabilities = listOf("snapshot.read", "cmd.write")
                        )
                    )
                )

                while (true) {
                    val line = readBoundedLine(reader) ?: break
                    when (val decoded = ProtocolCodec.decode(line)) {
                        is Decoded.Ok -> {
                            val env = decoded.envelope
                            HlcTimestamp.parse(env.ts)?.let { remote ->
                                runCatching { synchronized(clock) { clock.receive(remote) } }
                            }
                            onMessage(env)

                            when (val p = env.payload) {
                                is Hello -> if (!handshaken) {
                                    handshaken = true
                                    socket.soTimeout = IDLE_TIMEOUT_MS
                                    Log.i(TAG, "Session with ${p.deviceName} (${p.role}, app ${p.appVersion})")
                                    sendSnapshot(writer, env.id)

                                    // Start live continuous observer for water & signals
                                    s.launch {
                                        val startOfDay = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        val endOfDay = startOfDay + 86_400_000L

                                        db.waterDao().getTodayWater(startOfDay, endOfDay).collect { waterTotal ->
                                            val todaySignal = db.dailySignalsDao().getSignalForDate(LocalDate.now().toString())
                                            val screenMinutes = todaySignal?.screenTimeMinutes

                                            val dailySignalDto = DailySignalDto(
                                                date = LocalDate.now().toString(),
                                                heartRateBpm = todaySignal?.restingHr ?: 0,
                                                restingHeartRate = todaySignal?.restingHr ?: 0,
                                                hrv = todaySignal?.hrvMs?.toFloat() ?: 0f,
                                                stepCount = todaySignal?.steps ?: 0,
                                                activeEnergyBurned = todaySignal?.activeCaloriesKcal ?: 0,
                                                sleepDurationMinutes = todaySignal?.sleepMinutes ?: 0,
                                                sleepScore = todaySignal?.sleepMinutes?.let { (it * 100 / 480).coerceAtMost(100) },
                                                waterIntakeMl = waterTotal,
                                                activeMinutes = todaySignal?.workoutMinutes ?: 0,
                                                phoneUsageMinutes = screenMinutes,
                                                bloodOxygenPercentage = todaySignal?.spo2Percent?.toFloat() ?: 0f,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            synchronized(writer) {
                                                writer.println(frame(SignalsSnapshot(dailySignalDto)))
                                            }
                                        }
                                    }
                                }
                                is Ping -> writer.println(frame(Pong, env.id))
                                is LogWaterCommand -> {
                                    val waterEntry = WaterEntry(amountMl = p.amountMl, timestamp = System.currentTimeMillis())
                                    db.waterDao().insertWater(waterEntry)
                                    Log.i(TAG, "Logged ${p.amountMl} ml water from Desktop command")
                                    synchronized(writer) { writer.println(frame(Ack(env.id), env.id)) }
                                    sendSnapshot(writer, env.id)
                                }
                                is LogMealCommand -> {
                                    val meal = Meal(
                                        name = p.meal.name,
                                        mealType = "MEAL",
                                        calories = p.meal.calories,
                                        protein = p.meal.protein.toDouble(),
                                        carbohydrates = p.meal.carbs.toDouble(),
                                        fat = p.meal.fat.toDouble(),
                                        timestamp = if (p.meal.timestamp > 0) p.meal.timestamp else System.currentTimeMillis()
                                    )
                                    db.mealDao().insertMeal(meal)
                                    Log.i(TAG, "Logged meal '${p.meal.name}' from Desktop command")
                                    synchronized(writer) { writer.println(frame(Ack(env.id), env.id)) }
                                    sendSnapshot(writer, env.id)
                                }
                                is AddTaskCommand -> {
                                    val todo = TodoItem(
                                        title = p.title,
                                        isDone = false,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    db.todoDao().insert(todo)
                                    Log.i(TAG, "Added task '${p.title}' from Desktop command")
                                    synchronized(writer) { writer.println(frame(Ack(env.id), env.id)) }
                                    sendSnapshot(writer, env.id)
                                }
                                is SetTaskDoneCommand -> {
                                    val id = p.taskId.toLongOrNull()
                                    if (id != null) {
                                        val existing = db.todoDao().getById(id)
                                        if (existing != null) {
                                            db.todoDao().update(existing.copy(isDone = p.done))
                                        }
                                    }
                                    synchronized(writer) { writer.println(frame(Ack(env.id), env.id)) }
                                    sendSnapshot(writer, env.id)
                                }
                                else -> Unit
                            }
                        }
                        is Decoded.VersionMismatch -> {
                            writer.println(
                                frame(
                                    ErrorReply(
                                        ErrorCode.VERSION_MISMATCH,
                                        "Phone speaks protocol v$PROTOCOL_VERSION, you sent v${decoded.theirVersion}."
                                    ),
                                    decoded.id
                                )
                            )
                            break
                        }
                        is Decoded.Unsupported -> writer.println(
                            frame(
                                ErrorReply(ErrorCode.UNSUPPORTED_TYPE, "Unknown message type '${decoded.payloadType}'."),
                                decoded.id
                            )
                        )
                        is Decoded.Malformed -> {
                            Log.w(TAG, "Malformed message: ${decoded.reason}")
                            if (++malformed >= MAX_MALFORMED) break
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
                // handshake or idle timeout
            } catch (e: Exception) {
                Log.d(TAG, "Client ended: ${e.message}")
            } finally {
                clients.remove(socket)
                _connectedClients.update { (it - 1).coerceAtLeast(0) }
                runCatching { socket.close() }
            }
        }
    }

    private suspend fun sendSnapshot(writer: PrintWriter, replyTo: String) {
        val meals = (db.mealDao().getAllMeals().firstOrNull() ?: emptyList())
            .take(MAX_MEALS_SENT)
            .map { m ->
                FridayMealDto(
                    id = m.id.toString(),
                    name = m.name,
                    calories = m.calories,
                    protein = m.protein.toFloat(),
                    carbs = m.carbohydrates.toFloat(),
                    fat = m.fat.toFloat(),
                    timestamp = m.timestamp
                )
            }
        synchronized(writer) { writer.println(frame(MealsSnapshot(meals), replyTo)) }

        val tasks = (db.todoDao().getAll().firstOrNull() ?: emptyList()).map { t ->
            FridayTaskDto(
                id = t.id.toString(),
                title = t.title,
                isCompleted = t.isDone,
                category = "general",
                timestamp = t.createdAt
            )
        }
        synchronized(writer) { writer.println(frame(TasksSnapshot(tasks), replyTo)) }

        val reminders = (db.reminderDao().getAll().firstOrNull() ?: emptyList()).map { r ->
            FridayReminderDto(
                id = r.id.toString(),
                message = r.title,
                timeMillis = r.timeMillis,
                isTriggered = !r.enabled
            )
        }
        synchronized(writer) { writer.println(frame(RemindersSnapshot(reminders), replyTo)) }

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todaySignal = db.dailySignalsDao().getSignalForDate(LocalDate.now().toString())
        val water = todaySignal?.waterMl ?: db.waterDao().getTodayWater(startOfDay, startOfDay + 86_400_000L).firstOrNull()
        val screenMinutes = todaySignal?.screenTimeMinutes

        val dailySignalDto = DailySignalDto(
            date = LocalDate.now().toString(),
            heartRateBpm = todaySignal?.restingHr ?: 0,
            restingHeartRate = todaySignal?.restingHr ?: 0,
            hrv = todaySignal?.hrvMs?.toFloat() ?: 0f,
            stepCount = todaySignal?.steps ?: 0,
            activeEnergyBurned = todaySignal?.activeCaloriesKcal ?: 0,
            sleepDurationMinutes = todaySignal?.sleepMinutes ?: 0,
            sleepScore = todaySignal?.sleepMinutes?.let { (it * 100 / 480).coerceAtMost(100) },
            waterIntakeMl = water,
            activeMinutes = todaySignal?.workoutMinutes ?: 0,
            phoneUsageMinutes = screenMinutes,
            bloodOxygenPercentage = todaySignal?.spo2Percent?.toFloat() ?: 0f,
            timestamp = System.currentTimeMillis()
        )

        synchronized(writer) { writer.println(frame(SignalsSnapshot(dailySignalDto), replyTo)) }
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

    @Synchronized
    fun stopServer() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        scope?.cancel()
        scope = null
        _connectedClients.value = 0
    }
}
