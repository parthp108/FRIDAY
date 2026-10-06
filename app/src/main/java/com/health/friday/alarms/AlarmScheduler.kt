
package com.health.friday.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Calendar

class AlarmScheduler(
    private val context: Context
) {

    /*
     * FRIDAY now delegates normal alarms to the phone's
     * Clock application.
     *
     * The rest of FRIDAY still talks to AlarmScheduler,
     * so the alarm domain does not need to know which
     * execution backend is being used.
     */

    fun canScheduleExactAlarms(): Boolean {
        /*
         * The phone Clock app owns the actual alarm.
         *
         * FRIDAY no longer needs its own exact-alarm scheduling
         * for normal Clock-app alarms.
         */
        return true
    }

    fun openExactAlarmSettings() {
        /*
         * Kept for compatibility with the existing repository.
         *
         * The phone Clock app is now responsible for alarm
         * scheduling, so FRIDAY does not need to open its own
         * exact-alarm settings.
         */
    }

    fun scheduleAlarm(
        alarmId: Long,
        title: String,
        triggerAtMillis: Long,
        repeatDaily: Boolean,
        requestCode: Int = alarmId.toInt()
    ): Boolean {

        val target =
            Calendar.getInstance().apply {
                timeInMillis = triggerAtMillis
            }

        val hour =
            target.get(Calendar.HOUR_OF_DAY)

        val minute =
            target.get(Calendar.MINUTE)

        val intent =
            Intent(
                AlarmClock.ACTION_SET_ALARM
            ).apply {

                putExtra(
                    AlarmClock.EXTRA_HOUR,
                    hour
                )

                putExtra(
                    AlarmClock.EXTRA_MINUTES,
                    minute
                )

                putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    title
                )

                putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    true
                )

                if (repeatDaily) {

                    putExtra(
                        AlarmClock.EXTRA_DAYS,
                        arrayListOf(
                            Calendar.SUNDAY,
                            Calendar.MONDAY,
                            Calendar.TUESDAY,
                            Calendar.WEDNESDAY,
                            Calendar.THURSDAY,
                            Calendar.FRIDAY,
                            Calendar.SATURDAY
                        )
                    )
                }
            }

        return try {

            if (
                intent.resolveActivity(
                    context.packageManager
                ) == null
            ) {
                false
            } else {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(intent)

                true
            }

        } catch (_: Exception) {

            false
        }
    }

    fun cancelAlarm(
        alarmId: Long,
        requestCode: Int = alarmId.toInt()
    ) {
        /*
         * Android's public AlarmClock intent API does not provide
         * a reliable generic delete operation for an alarm created
         * by another Clock application.
         *
         * FRIDAY can still delete/disable its own database record,
         * but it does not pretend that this removes the Clock alarm.
         */
    }

    companion object {

        const val EXTRA_ALARM_ID =
            "extra_alarm_id"

        const val EXTRA_TITLE =
            "extra_alarm_title"

        const val EXTRA_REPEAT_DAILY =
            "extra_repeat_daily"
    }
}

