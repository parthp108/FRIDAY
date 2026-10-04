
package com.health.friday.alarms

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.health.friday.data.local.FridayDatabase
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    private var alarmId: Long = -1L

    private var alarmTitle by mutableStateOf(
        "FRIDAY alarm"
    )

    private var repeatDaily by mutableStateOf(
        false
    )

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        alarmId =
            intent.getLongExtra(
                AlarmScheduler.EXTRA_ALARM_ID,
                -1L
            )

        alarmTitle =
            intent.getStringExtra(
                AlarmScheduler.EXTRA_TITLE
            ) ?: "FRIDAY alarm"

        repeatDaily =
            intent.getBooleanExtra(
                AlarmScheduler.EXTRA_REPEAT_DAILY,
                false
            )

        makeAlarmScreenVisible()

        /*
         * Back must not dismiss the alarm.
         */
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    // Intentionally do nothing.
                    // The alarm can only be stopped through
                    // Dismiss or Snooze.
                }
            }
        )

        startAlarmSound()

        setContent {

            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {

                AlarmScreen(
                    alarmTitle = alarmTitle,

                    onDismiss = {
                        dismissAlarm()
                    },

                    onSnooze = {
                        snoozeAlarm()
                    }
                )
            }
        }
    }

    private fun makeAlarmScreenVisible() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O_MR1
        ) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private fun startAlarmSound() {

        stopAlarmSound()

        val alarmUri =
            RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_ALARM
            )

        mediaPlayer =
            MediaPlayer.create(
                this,
                alarmUri
            )

        mediaPlayer?.apply {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP
            ) {

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )
            }

            isLooping = true

            start()
        }
    }

    private fun stopAlarmSound() {

        mediaPlayer?.let { player ->

            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {
            }

            try {
                player.release()
            } catch (_: Exception) {
            }
        }

        mediaPlayer = null
    }

    private fun dismissAlarm() {

        stopAlarmSound()

        cancelAlarmNotification()

        if (
            alarmId != -1L &&
            !repeatDaily
        ) {

            val database =
                FridayDatabase.getDatabase(
                    applicationContext
                )

            lifecycleScope.launch {

                database.alarmDao().disable(
                    alarmId
                )

                finish()
            }

        } else {

            finish()
        }
    }

    private fun snoozeAlarm() {

        stopAlarmSound()

        cancelAlarmNotification()

        if (alarmId == -1L) {
            finish()
            return
        }

        val scheduler =
            AlarmScheduler(
                applicationContext
            )

        scheduler.scheduleAlarm(
            alarmId = alarmId,
            title = alarmTitle,
            triggerAtMillis =
                System.currentTimeMillis() +
                        1L * 60L * 1000L,
            repeatDaily = false,
            requestCode =
                alarmId.toInt() + 1_000_000
        )

        if (!repeatDaily) {

            val database =
                FridayDatabase.getDatabase(
                    applicationContext
                )

            lifecycleScope.launch {

                database.alarmDao().disable(
                    alarmId
                )

                finish()
            }

        } else {

            finish()
        }
    }

    private fun cancelAlarmNotification() {

        val notificationManager =
            getSystemService(
                NotificationManager::class.java
            )

        notificationManager.cancel(
            AlarmReceiver.notificationId(
                alarmId
            )
        )
    }

    /*
     * Ignore hardware key events while the alarm is active.
     *
     * This prevents volume-key interaction from being treated
     * as an alarm dismissal by this activity.
     */


    override fun onDestroy() {

        stopAlarmSound()

        super.onDestroy()
    }
}

