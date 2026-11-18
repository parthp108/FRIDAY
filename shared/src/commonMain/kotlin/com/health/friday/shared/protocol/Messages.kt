package com.health.friday.shared.protocol

import com.health.friday.shared.models.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PROTOCOL_VERSION = 1

@Serializable
enum class DeviceRole { PHONE, DESKTOP }

@Serializable
enum class ErrorCode { VERSION_MISMATCH, UNSUPPORTED_TYPE, READ_ONLY, MALFORMED, NOT_READY, INTERNAL }

/** Every message body. The JSON discriminator is the @SerialName, e.g. {"type":"hello",...}. */
@Serializable
sealed interface Payload

@Serializable @SerialName("hello")
data class Hello(
    val deviceId: String,
    val deviceName: String,
    val role: DeviceRole,
    val appVersion: String,
    val capabilities: List<String> = emptyList()
) : Payload

@Serializable @SerialName("ping") data object Ping : Payload
@Serializable @SerialName("pong") data object Pong : Payload

// Phone -> desktop snapshots (live change feed arrives in a later phase)
@Serializable @SerialName("snapshot.meals")
data class MealsSnapshot(val meals: List<FridayMealDto>) : Payload

@Serializable @SerialName("snapshot.tasks")
data class TasksSnapshot(val tasks: List<FridayTaskDto>) : Payload

@Serializable @SerialName("snapshot.reminders")
data class RemindersSnapshot(val reminders: List<FridayReminderDto>) : Payload

@Serializable @SerialName("snapshot.signals")
data class SignalsSnapshot(val signals: DailySignalDto) : Payload

@Serializable @SerialName("snapshot.home")
data class HomeSnapshot(val dashboard: HomeDashboardSnapshot) : Payload

@Serializable @SerialName("snapshot.journals")
data class JournalsSnapshot(val entries: List<JournalEntryDto>) : Payload

@Serializable @SerialName("snapshot.chat_sessions")
data class ChatSessionsSnapshot(val sessions: List<ChatSessionDto>) : Payload

@Serializable @SerialName("snapshot.chat_messages")
data class ChatMessagesSnapshot(val sessionId: String, val messages: List<ChatMessageDto>) : Payload

// Desktop -> phone commands (executed by the phone's own repositories in a later phase)
@Serializable @SerialName("cmd.log_meal")
data class LogMealCommand(val meal: FridayMealDto) : Payload

@Serializable @SerialName("cmd.log_water")
data class LogWaterCommand(val amountMl: Int) : Payload

@Serializable @SerialName("cmd.add_task")
data class AddTaskCommand(val title: String) : Payload

@Serializable @SerialName("cmd.set_task_done")
data class SetTaskDoneCommand(val taskId: String, val done: Boolean) : Payload

// Replies
@Serializable @SerialName("ack")
data class Ack(val resultId: String? = null) : Payload

@Serializable @SerialName("error")
data class ErrorReply(val code: ErrorCode, val message: String) : Payload

object Payloads {
    /** Must list every Payload subtype. A test fails if this drifts from the real set. */
    val KNOWN_TYPES: Set<String> = setOf(
        "hello", "ping", "pong",
        "snapshot.meals", "snapshot.tasks", "snapshot.reminders", "snapshot.signals",
        "snapshot.home", "snapshot.journals", "snapshot.chat_sessions", "snapshot.chat_messages",
        "cmd.log_meal", "cmd.log_water", "cmd.add_task", "cmd.set_task_done",
        "ack", "error"
    )
}

@Serializable
data class Envelope(
    val v: Int = PROTOCOL_VERSION,
    val id: String,
    val replyTo: String? = null,
    val from: String,
    val ts: String,
    val payload: Payload
)

/** Builds an outgoing envelope stamped by [clock]. */
fun createEnvelope(clock: HybridLogicalClock, payload: Payload, replyTo: String? = null): Envelope =
    Envelope(
        id = Ids.newMessageId(),
        replyTo = replyTo,
        from = clock.nodeId,
        ts = clock.now().encode(),
        payload = payload
    )
