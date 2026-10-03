package com.health.friday.device

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsage(
    val label: String,
    val millis: Long
)

data class UsageSummary(
    val totalMillis: Long,
    val topApps: List<AppUsage>
)

class DeviceUsageRepository(
    context: Context
) {

    private val appContext = context.applicationContext

    fun hasAccess(): Boolean {

        val appOps =
            appContext.getSystemService(Context.APP_OPS_SERVICE)
                    as AppOpsManager

        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName
                )
            }

        // Some devices report MODE_DEFAULT even when access is granted.
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            appContext.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    suspend fun getTodayUsage(): UsageSummary =
        withContext(Dispatchers.Default) {

            val manager =
                appContext.getSystemService(Context.USAGE_STATS_SERVICE)
                        as UsageStatsManager

            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val startOfDay = calendar.timeInMillis

            val events = manager.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()

            // key = "package/activity" -> time it came to the foreground
            val resumedAt = HashMap<String, Long>()

            // package -> total foreground millis
            val totals = HashMap<String, Long>()

            while (events.hasNextEvent()) {

                events.getNextEvent(event)

                val pkg = event.packageName ?: continue
                val key = pkg + "/" + (event.className ?: "")

                when (event.eventType) {

                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        resumedAt[key] = event.timeStamp
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {

                        val started = resumedAt.remove(key)

                        if (started != null && event.timeStamp > started) {
                            totals[pkg] =
                                (totals[pkg] ?: 0L) +
                                        (event.timeStamp - started)
                        }
                    }
                }
            }

            // Activities still open right now count up to "now",
            // but only if the screen is actually on.
            val powerManager =
                appContext.getSystemService(Context.POWER_SERVICE)
                        as PowerManager

            if (powerManager.isInteractive) {
                for ((key, started) in resumedAt) {

                    val pkg = key.substringBefore("/")

                    if (now > started) {
                        totals[pkg] =
                            (totals[pkg] ?: 0L) + (now - started)
                    }
                }
            }

            val excluded = excludedPackages()

            val counted =
                totals.filterKeys { it !in excluded }

            val topApps =
                counted.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { AppUsage(labelFor(it.key), it.value) }

            UsageSummary(
                totalMillis = counted.values.sum(),
                topApps = topApps
            )
        }

    private fun excludedPackages(): Set<String> {

        val homeIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)

        val launchers =
            appContext.packageManager
                .queryIntentActivities(homeIntent, 0)
                .map { it.activityInfo.packageName }

        return (launchers + "com.android.systemui" + "android").toSet()
    }

    private fun labelFor(packageName: String): String {

        return try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}