package com.health.friday.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.health.friday.MainActivity
import com.health.friday.R
import com.health.friday.data.local.DailySignalsDao
import com.health.friday.proactive.NudgePolicy
import com.health.friday.stats.StatEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnomalyDetector(
    private val context: Context,
    private val signalsDao: DailySignalsDao,
    private val nudgePolicy: NudgePolicy
) {
    /**
     * Rolling-baseline detector. For each metric, the 30 days BEFORE today give a mean (mu) and
     * standard deviation (sigma); today is flagged when it is more than 2 sigma on the bad side
     * (resting HR above mu + 2 sigma, sleep below mu - 2 sigma). Steps are not flagged because
     * today's step count is still partial when this runs.
     */
    suspend fun detectAndNotify() = withContext(Dispatchers.IO) {
        val recent = signalsDao.getRecentSignals(BASELINE_DAYS + 1).sortedByDescending { it.date }
        val today = recent.firstOrNull() ?: return@withContext
        // Never judge a stale row as "today".
        if (today.date != java.time.LocalDate.now().toString()) return@withContext
        val baseline = recent.drop(1)

        val hrFlag = today.restingHr?.let { hr ->
            score(baseline.mapNotNull { it.restingHr?.toDouble() }, hr, MIN_SIGMA_HR)
                ?.takeIf { it.z > Z_THRESHOLD }
        }
        val sleepLow = today.sleepMinutes?.let { sl ->
            score(baseline.mapNotNull { it.sleepMinutes?.toDouble() }, sl, MIN_SIGMA_SLEEP)
                ?.takeIf { it.z < -Z_THRESHOLD }
        }

        if (hrFlag != null && sleepLow != null && nudgePolicy.canNudge("anomaly_combined")) {
            sendAlert(
                "Possible under-recovery",
                "Resting HR ${hrFlag.value} bpm (your norm ${hrFlag.mean.toInt()}) while sleep was " +
                    "${sleepLow.value} min (your norm ${sleepLow.mean.toInt()}). Both are unusual for you, " +
                    "which can mean you're fighting something off or under-recovered. Take it easy and watch how you feel."
            )
            nudgePolicy.recordNudge("anomaly_combined")
            return@withContext
        }

        if (hrFlag != null && nudgePolicy.canNudge("anomaly_hr")) {
            sendAlert(
                "Elevated Heart Rate",
                "Resting HR is ${hrFlag.value} bpm, well above your usual ${hrFlag.mean.toInt()} bpm " +
                    "(more than 2 standard deviations). Stress, poor recovery or illness can do this."
            )
            nudgePolicy.recordNudge("anomaly_hr")
        }

        if (sleepLow != null && nudgePolicy.canNudge("anomaly_sleep")) {
            sendAlert(
                "Unusually Short Sleep",
                "You slept ${sleepLow.value} min, well below your usual ${sleepLow.mean.toInt()} min. " +
                    "Consider prioritizing rest tonight."
            )
            nudgePolicy.recordNudge("anomaly_sleep")
        }
    }

    private data class Score(val value: Int, val mean: Double, val sigma: Double, val z: Double)

    /** Today's value against the baseline history, or null if there is too little history. */
    private fun score(history: List<Double>, value: Int, minSigma: Double): Score? {
        if (history.size < MIN_BASELINE_POINTS) return null
        val mu = StatEngine.mean(history)
        val sigma = maxOf(StatEngine.stdDev(history), minSigma) // floor stops a tiny sigma causing false alarms
        return Score(value, mu, sigma, (value - mu) / sigma)
    }

    private fun sendAlert(title: String, message: String) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "friday_anomalies"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "FRIDAY Health Alerts", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher) // Ensure you have this basic icon
                .setContentTitle("FRIDAY: $title")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            manager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private companion object {
        const val BASELINE_DAYS = 30
        const val MIN_BASELINE_POINTS = 7
        const val Z_THRESHOLD = 2.0
        const val MIN_SIGMA_HR = 1.5      // bpm
        const val MIN_SIGMA_SLEEP = 20.0  // minutes
    }
}
