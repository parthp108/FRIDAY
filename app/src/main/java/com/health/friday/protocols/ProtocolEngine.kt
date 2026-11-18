package com.health.friday.protocols

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.health.friday.MainActivity
import com.health.friday.R
import com.health.friday.ai.AiToolRegistry
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.NotificationActionReceiver
import com.health.friday.proactive.NudgePolicy
import com.health.friday.proactive.ProactiveDao
import com.health.friday.proactive.ProtocolEntity
import com.health.friday.util.DayRange
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * The "If-This-Then-That" engine. It never calls the LLM: when a protocol's condition
 * is true it looks the tool up in [AiToolRegistry] and executes it directly.
 *
 * Trigger JSON (leaf):  {"metric":"hour","operator":">=","value":22}
 * Combine with "and" / "or" (an object or an array of objects), nested as deep as needed:
 *   {"metric":"hour","operator":">=","value":22,
 *    "and":{"metric":"screenTimeMinutes","operator":">","value":120}}
 *
 * Metrics: hour, steps, water (waterMl), kcal, protein, carbs, fat, sleepMinutes,
 * screenTimeMinutes, restingHr, lastMealHour, tasksPending, tasksCompleted, workoutMinutes.
 * Unknown (null) data never satisfies a condition.
 *
 * Autonomy levels: SILENT_AUTO (run), TELL_AFTER (run, then notify),
 * ASK_FIRST (notify with a "Run it" button). Anything else is treated as ASK_FIRST.
 */
class ProtocolEngine(
    private val context: Context,
    private val proactiveDao: ProactiveDao,
    private val signalsDao: DailySignalsDao,
    private val toolRegistry: AiToolRegistry
) {

    /** Seeds the example protocol on first run so the engine has something to evaluate. */
    suspend fun seedDefaultsIfEmpty() {
        if (proactiveDao.getProtocolCount() > 0) return
        proactiveDao.insertProtocol(
            ProtocolEntity(
                title = "Late Night Lock Down",
                triggerConditionJson =
                    """{"metric":"hour","operator":">=","value":22,"and":{"metric":"screenTimeMinutes","operator":">","value":120}}""",
                actionTool = "start_focus_mode",
                actionPayloadJson = """{"state":"on"}""",
                autonomyLevel = "ASK_FIRST"
            )
        )
    }

    /** Called by HealthTelemetryWorker right after it has refreshed today's daily_signals row. */
    suspend fun evaluateAndTrigger() {
        val protocols = proactiveDao.getActiveProtocols()
        if (protocols.isEmpty()) return

        // Must be TODAY's row; the newest row could be stale if today was never built.
        val todayStr = LocalDate.now().toString()
        val signals = signalsDao.getSignalForDate(todayStr) ?: return

        for (protocol in protocols) {
            try {
                val condition = JSONObject(protocol.triggerConditionJson)
                if (!evaluate(condition, signals)) continue
                if (alreadyFiredToday(protocol)) continue

                // Claim the slot first so a slow/failed action can't fire twice in one day.
                proactiveDao.insertNudge(
                    com.health.friday.proactive.NudgeHistoryEntity(
                        type = nudgeType(protocol),
                        timestamp = System.currentTimeMillis(),
                        interacted = false
                    )
                )
                dispatch(protocol)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping protocol '${protocol.title}'", e)
            }
        }
    }

    /** Runs a protocol's action immediately (used by the notification "Run it" button). */
    suspend fun runProtocolById(id: Long): Boolean {
        val protocol = proactiveDao.getProtocolById(id) ?: return false
        return executeAction(protocol) != null
    }

    // ---- condition evaluation -------------------------------------------------------------

    private fun evaluate(node: JSONObject, s: DailySignalsEntity): Boolean {
        var result = if (node.has("metric")) evaluateLeaf(node, s) else true

        if (node.has("and")) {
            result = result && allOf(node.get("and"), s)
        }
        if (node.has("or")) {
            result = result || anyOf(node.get("or"), s)
        }
        return result
    }

    private fun allOf(value: Any, s: DailySignalsEntity): Boolean = when (value) {
        is JSONObject -> evaluate(value, s)
        is JSONArray -> (0 until value.length()).all { evaluate(value.getJSONObject(it), s) }
        else -> false
    }

    private fun anyOf(value: Any, s: DailySignalsEntity): Boolean = when (value) {
        is JSONObject -> evaluate(value, s)
        is JSONArray -> (0 until value.length()).any { evaluate(value.getJSONObject(it), s) }
        else -> false
    }

    private fun evaluateLeaf(node: JSONObject, s: DailySignalsEntity): Boolean {
        val metric = node.getString("metric")
        val operator = node.getString("operator")
        val target = node.getDouble("value")

        val actual: Double = metricValue(metric, s) ?: return false

        return when (operator) {
            "<" -> actual < target
            "<=" -> actual <= target
            ">" -> actual > target
            ">=" -> actual >= target
            "==" -> actual == target
            "!=" -> actual != target
            else -> false
        }
    }

    private fun metricValue(metric: String, s: DailySignalsEntity): Double? = when (metric) {
        "hour" -> LocalTime.now().let { it.hour + it.minute / 60.0 }
        "steps" -> s.steps?.toDouble()
        "water", "waterMl" -> s.waterMl?.toDouble()
        "kcal" -> s.kcal?.toDouble()
        "protein" -> s.proteinGrams?.toDouble()
        "carbs" -> s.carbsGrams?.toDouble()
        "fat" -> s.fatGrams?.toDouble()
        "sleepMinutes" -> s.sleepMinutes?.toDouble()
        "screenTimeMinutes" -> s.screenTimeMinutes?.toDouble()
        "restingHr" -> s.restingHr?.toDouble()
        "lastMealHour" -> s.lastMealHour?.toDouble()
        "tasksPending" -> s.tasksPending.toDouble()
        "tasksCompleted" -> s.tasksCompleted.toDouble()
        "workoutMinutes" -> s.workoutMinutes.toDouble()
        else -> null
    }

    // ---- throttling -----------------------------------------------------------------------

    private fun nudgeType(p: ProtocolEntity) = NudgePolicy.PROTOCOL_PREFIX + p.id

    /** A protocol fires at most once per day, otherwise every 4-hour worker run would repeat it. */
    private suspend fun alreadyFiredToday(p: ProtocolEntity): Boolean {
        val last = proactiveDao.getLastNudgeOfType(nudgeType(p)) ?: return false
        return last.timestamp >= DayRange.today().first
    }

    // ---- action dispatch ------------------------------------------------------------------

    private suspend fun dispatch(protocol: ProtocolEntity) {
        when (protocol.autonomyLevel.uppercase()) {
            "SILENT_AUTO", "AUTO_EXECUTE", "AUTO-EXECUTE" -> executeAction(protocol)
            "TELL_AFTER" -> {
                val output = executeAction(protocol)
                notifyUser(
                    protocol,
                    "Done: ${protocol.title}",
                    output ?: "I tried to run it, but it didn't work.",
                    askFirst = false
                )
            }
            else -> notifyUser(
                protocol,
                protocol.title,
                "Conditions met. Want me to run \"${protocol.actionTool}\"?",
                askFirst = true
            )
        }
    }

    /** Executes the protocol's tool straight from the registry. Returns the tool output, or null on failure. */
    private suspend fun executeAction(protocol: ProtocolEntity): String? {
        val tool = toolRegistry.getTool(protocol.actionTool)
        if (tool == null) {
            Log.w(TAG, "No tool named '${protocol.actionTool}' for protocol '${protocol.title}'")
            return null
        }

        return try {
            val payload = JSONObject(protocol.actionPayloadJson.ifBlank { "{}" })
            val args = mutableMapOf<String, String>()
            payload.keys().forEach { key -> args[key] = payload.get(key).toString() }

            val output = tool.execute(args)
            Log.i(TAG, "Protocol '${protocol.title}' executed ${tool.name}: $output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "Protocol '${protocol.title}' failed", e)
            null
        }
    }

    private fun notifyUser(protocol: ProtocolEntity, title: String, text: String, askFirst: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FRIDAY Protocols", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val notificationId = NOTIFICATION_BASE_ID + protocol.id.toInt()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)

        fun action(name: String, requestCode: Int): PendingIntent {
            val i = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = name
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(NotificationActionReceiver.EXTRA_NUDGE_TYPE, nudgeType(protocol))
                putExtra(NotificationActionReceiver.EXTRA_PROTOCOL_ID, protocol.id)
            }
            return PendingIntent.getBroadcast(context, requestCode + protocol.id.toInt() * 10, i, flags)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)

        if (askFirst) {
            builder.addAction(0, "Run it", action(NotificationActionReceiver.ACTION_RUN_PROTOCOL, 1))
        }
        builder.addAction(0, "Dismiss", action(NotificationActionReceiver.ACTION_DISMISS, 2))

        manager.notify(notificationId, builder.build())
    }

    companion object {
        private const val TAG = "FRIDAY_PROTOCOL"
        private const val CHANNEL_ID = "friday_protocols"
        private const val NOTIFICATION_BASE_ID = 2000
    }
}
