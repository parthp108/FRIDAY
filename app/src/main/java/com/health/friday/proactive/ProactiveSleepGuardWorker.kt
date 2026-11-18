package com.health.friday.proactive

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.health.friday.FridayApplication
import com.health.friday.alarms.AlarmReceiver
import com.health.friday.alarms.AlarmRingingService
import com.health.friday.alarms.AlarmScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ProactiveSleepGuardWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? FridayApplication ?: return Result.success()
        val container = app.container

        if (!container.nudgePolicy.canNudge(NUDGE_TYPE)) {
            return Result.success()
        }

        val now = LocalTime.now()
        // Target wake time: 7:30 AM
        val targetWakeTime = LocalTime.of(7, 30)

        // Only check during morning window (7:30 AM to 8:30 AM)
        if (now.isBefore(targetWakeTime) || now.isAfter(LocalTime.of(8, 30))) {
            return Result.success()
        }

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        runCatching {
            val dayHealth = container.healthConnectRepository.getDayHealth(startOfDay)
            val sleepEnd = dayHealth?.sleepEndMillis

            val targetWakeEpoch = today.atTime(targetWakeTime).atZone(zoneId).toInstant().toEpochMilli()

            // If sleep end-time is unlogged or recorded wake-up is past target wake time
            val needsWakeAlert = sleepEnd == null || sleepEnd > targetWakeEpoch

            if (needsWakeAlert) {
                val nudgeId = container.nudgePolicy.recordNudge(NUDGE_TYPE)
                Log.i("SleepGuardWorker", "Triggering 7:30 AM Wake Guard Alert & Ringing Alarm")

                // Start Alarm Ringing Service (looping sound + ongoing notification)
                AlarmRingingService.startService(
                    context,
                    AlarmScheduler.SLEEP_GUARD_ALARM_ID,
                    "7:30 AM Wake Up Call - Time to get out of bed!",
                    true
                )

                // Post full screen notification and launch activity over lockscreen
                AlarmReceiver.showAlarmNotification(
                    context,
                    AlarmScheduler.SLEEP_GUARD_ALARM_ID,
                    "7:30 AM Wake Up Call - Time to get out of bed!",
                    true
                )

                ProactiveVoiceActivity.launch(
                    context = context,
                    message = "Hey boss, it's 7:30 AM! Time to wake up and start your day!",
                    nudgeType = NUDGE_TYPE,
                    nudgeId = nudgeId
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday_proactive_sleep_guard"
        const val NUDGE_TYPE = "sleep_guard_alert"

        fun scheduleExactAlarm(context: Context) {
            val zone = ZoneId.systemDefault()
            var targetTime = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(7, 30), zone)
            if (targetTime.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
                targetTime = targetTime.plusDays(1)
            }
            val scheduler = AlarmScheduler(context)
            scheduler.scheduleAlarm(
                alarmId = AlarmScheduler.SLEEP_GUARD_ALARM_ID,
                title = "7:30 AM Wake Up Call",
                triggerAtMillis = targetTime.toInstant().toEpochMilli(),
                repeatDaily = true
            )
            Log.i("SleepGuardWorker", "Scheduled exact 7:30 AM wake guard alarm for $targetTime")
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveSleepGuardWorker>()
                .addTag("friday_sleep_guard_debug")
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun schedule(context: Context) {
            // 1. Schedule Exact Alarm via AlarmManager for guaranteed 7:30 AM trigger
            scheduleExactAlarm(context)

            // 2. Schedule Periodic WorkManager task as a fallback
            val request = PeriodicWorkRequestBuilder<ProactiveSleepGuardWorker>(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
