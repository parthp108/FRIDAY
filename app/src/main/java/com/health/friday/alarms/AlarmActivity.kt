package com.health.friday.alarms

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.Calendar

class AlarmActivity : ComponentActivity() {

    private var alarmId: Long = -1L
    private var alarmTitle by mutableStateOf("FRIDAY alarm")
    private var repeatDaily by mutableStateOf(false)

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarmSound()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Full screen over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        super.onCreate(savedInstanceState)

        parseIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally do nothing.
                // The alarm can only be stopped through Dismiss or Snooze.
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, IntentFilter(ACTION_FINISH_ALARM), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(finishReceiver, IntentFilter(ACTION_FINISH_ALARM))
        }

        // Handle daily repeat rescheduling when triggered
        if (alarmId != -1L && repeatDaily) {
            rescheduleDailyAlarm()
        }

        // Start foreground service (handles ongoing notification and looping sound)
        if (alarmId != -1L) {
            AlarmRingingService.startService(this, alarmId, alarmTitle, repeatDaily)
        }

        setContent {
            FRIDAYTheme(darkTheme = true, dynamicColor = false) {
                AlarmScreen(
                    alarmTitle = alarmTitle,
                    onDismiss = { dismissAlarm() },
                    onSnooze = { snoozeAlarm() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
        startAlarmSound()
    }

    private fun parseIntent(intent: Intent?) {
        intent?.let {
            alarmId = it.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
            alarmTitle = it.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "FRIDAY alarm"
            repeatDaily = it.getBooleanExtra(AlarmScheduler.EXTRA_REPEAT_DAILY, false)
        }
    }

    private fun rescheduleDailyAlarm() {
        val scheduler = AlarmScheduler(applicationContext)
        val nextTrigger = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        scheduler.scheduleAlarm(
            alarmId = alarmId,
            title = alarmTitle,
            triggerAtMillis = nextTrigger,
            repeatDaily = true
        )
    }

    private fun startAlarmSound() {
        if (alarmId != -1L) {
            AlarmRingingService.startService(this, alarmId, alarmTitle, repeatDaily)
        }
    }

    private fun stopAlarmSound() {
        AlarmRingingService.stopService(this)
    }

    private fun dismissAlarm() {
        stopAlarmSound()

        if (alarmId != -1L && !repeatDaily) {
            val database = FridayDatabase.getDatabase(applicationContext)
            lifecycleScope.launch {
                database.alarmDao().disable(alarmId)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun snoozeAlarm() {
        stopAlarmSound()

        if (alarmId == -1L) {
            finish()
            return
        }

        val scheduler = AlarmScheduler(applicationContext)
        scheduler.scheduleAlarm(
            alarmId = alarmId,
            title = alarmTitle,
            triggerAtMillis = System.currentTimeMillis() + (10 * 60 * 1000L), // 10 minutes
            repeatDaily = false,
            requestCode = alarmId.toInt() + 1_000_000
        )

        if (!repeatDaily) {
            val database = FridayDatabase.getDatabase(applicationContext)
            lifecycleScope.launch {
                database.alarmDao().disable(alarmId)
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        stopAlarmSound()
        try {
            unregisterReceiver(finishReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_FINISH_ALARM = "com.health.friday.alarms.ACTION_FINISH_ALARM"
    }
}