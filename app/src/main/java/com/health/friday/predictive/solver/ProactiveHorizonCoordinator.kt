package com.health.friday.predictive.solver

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.app.NotificationCompat
import com.health.friday.proactive.NudgePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProactiveHorizonCoordinator(
    private val context: Context,
    private val solver: SpatioTemporalSolver = SpatioTemporalSolver(),
    private val calendarReader: CalendarHorizonReader = CalendarHorizonReader(context),
    private val nudgePolicy: NudgePolicy
) {
    companion object {
        private const val TAG = "FRIDAY_PRECOMPUTE"
        private const val CHANNEL_ID = "friday_predictive_horizon"
        const val ACTION_AUTO_RESCHEDULE = "com.health.friday.ACTION_AUTO_RESCHEDULE"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_NEW_START_MS = "extra_new_start_ms"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var evaluationJob: Job? = null

    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            evaluationJob?.cancel()
            evaluationJob = scope.launch {
                delay(1500L)
                evaluateNextSixHours()
            }
        }
    }

    fun startObserving() {
        createNotificationChannel()
        runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                calendarObserver
            )
        }
        scope.launch { evaluateNextSixHours() }
    }

    fun stopObserving() {
        runCatching {
            context.contentResolver.unregisterContentObserver(calendarObserver)
        }
        scope.cancel()
    }

    /**
     * Runs the Tier 1 deterministic inequality & Gaussian CDF solver across the next 6 hours.
     * Only wakes up the notification/LLM layer if P(clash) >= 0.70.
     */
    suspend fun evaluateNextSixHours(
        currentLat: Double? = null,
        currentLon: Double? = null
    ): List<ScheduleClashPrediction> = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val upcoming = calendarReader.queryUpcomingHorizon(nowMs = nowMs, horizonHours = 6)

        val clashes = solver.detectHorizonClashes(
            nowEpochMs = nowMs,
            currentLat = currentLat,
            currentLon = currentLon,
            upcomingEvents = upcoming,
            prepBufferMinutes = 10.0,
            probabilityThreshold = 0.70
        )

        if (clashes.isNotEmpty()) {
            val topClash = clashes.maxBy { it.clashProbability }
            Log.i(
                TAG,
                "Predicted schedule clash: '${topClash.currentOrLeadingEvent.title}' -> " +
                        "'${topClash.conflictingNextEvent.title}' (P=${"%.2f".format(topClash.clashProbability)})"
            )
            if (nudgePolicy.canNudge("SCHEDULE_CLASH")) {
                dispatchClashInterventionAlert(topClash)
                nudgePolicy.recordNudge("SCHEDULE_CLASH")
            }
        }

        clashes
    }

    private fun dispatchClashInterventionAlert(clash: ScheduleClashPrediction) {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val newTimeFormatted = timeFmt.format(Date(clash.recommendedNewStartEpochMs))
        val probPct = (clash.clashProbability * 100).toInt()

        val message = "Boss, '${clash.currentOrLeadingEvent.title}' is trending long ($probPct% clash risk" +
                "${if (clash.estimatedTravelMinutes > 1.0) ", ${clash.estimatedTravelMinutes.toInt()}m transit" else ""}). " +
                "Push '${clash.conflictingNextEvent.title}' to $newTimeFormatted?"

        val rescheduleIntent = Intent(ACTION_AUTO_RESCHEDULE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_EVENT_ID, clash.conflictingNextEvent.eventId)
            putExtra(EXTRA_NEW_START_MS, clash.recommendedNewStartEpochMs)
        }

        val pendingReschedule = PendingIntent.getBroadcast(
            context,
            clash.conflictingNextEvent.eventId.toInt(),
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_agenda)
            .setContentTitle("FRIDAY // Horizon Conflict ($probPct%)")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_menu_recent_history,
                "Push to $newTimeFormatted",
                pendingReschedule
            )
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(8801, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FRIDAY Predictive Horizon",
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
