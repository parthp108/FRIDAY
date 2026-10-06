
package com.health.friday.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Calendar

class AlarmScheduler(
    private val context: Context
) {

    fun canScheduleExactAlarms(): Boolean {
        return true
    }

    fun openExactAlarmSettings() {
        // The phone Clock app handles its own alarm permission/settings.
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

        val clockIntent =
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

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        return try {

            if (
                clockIntent.resolveActivity(
                    context.packageManager
                ) == null
            ) {
                return false
            }

            context.startActivity(clockIntent)

            /*
             * Give the Clock app a moment to process the
             * ACTION_SET_ALARM request, then bring FRIDAY
             * back to the foreground.
             *
             * This does not control the Clock app's window.
             * It simply attempts to return the user to FRIDAY.
             */
            android.os.Handler(
                context.mainLooper
            ).postDelayed(
                {
                    bringFridayToForeground()
                },
                300L
            )

            true

        } catch (_: Exception) {
            false
        }
    }

    private fun bringFridayToForeground() {

        val intent =
            context.packageManager
                .getLaunchIntentForPackage(
                    context.packageName
                )
                ?: return

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun cancelAlarm(
        alarmId: Long,
        requestCode: Int = alarmId.toInt()
    ) {
        /*
         * The Android public AlarmClock API does not provide
         * a generic way for FRIDAY to delete an alarm that
         * was created inside the manufacturer's Clock app.
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

