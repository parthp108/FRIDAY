package com.health.friday.proactive

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.health.friday.FridayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val nudgeType = intent.getStringExtra(EXTRA_NUDGE_TYPE) ?: "unknown"
        val nudgeId = intent.getLongExtra(EXTRA_NUDGE_ID, -1L)
        val protocolId = intent.getLongExtra(EXTRA_PROTOCOL_ID, -1L)

        val container = (context.applicationContext as FridayApplication).container

        // goAsync keeps the process alive until the background work below has finished.
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Any tap on a button teaches the Anti-Annoyance system the nudge was seen.
                if (action != ACTION_DISMISS) {
                    if (nudgeId != -1L) {
                        container.nudgePolicy.markInteracted(nudgeId)
                    } else {
                        container.nudgePolicy.markInteracted(nudgeType)
                    }
                }

                when (action) {
                    ACTION_ADD_WATER_250 -> {
                        container.waterRepository.addWater(250)
                    }
                    ACTION_RUN_PROTOCOL -> {
                        if (protocolId != -1L) {
                            container.protocolEngine.runProtocolById(protocolId)
                        }
                    }
                    ACTION_SNOOZE_15M, ACTION_DISMISS -> {
                        // Dismiss is deliberately NOT counted as interaction.
                    }
                    ACTION_MARK_TASKS_DONE -> {
                        val todos = container.todoRepository.getTodosNow().filter { !it.isDone }
                        todos.forEach { container.todoRepository.setDone(it, true) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notification action failed: $action", e)
            } finally {
                if (notificationId != -1) {
                    val manager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notificationId)
                }
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"

        const val ACTION_ADD_WATER_250 = "ACTION_ADD_WATER_250"
        const val ACTION_SNOOZE_15M = "ACTION_SNOOZE_15M"
        const val ACTION_MARK_TASKS_DONE = "ACTION_MARK_TASKS_DONE"
        const val ACTION_RUN_PROTOCOL = "ACTION_RUN_PROTOCOL"
        const val ACTION_DISMISS = "ACTION_DISMISS"

        const val EXTRA_NOTIFICATION_ID = "NOTIFICATION_ID"
        const val EXTRA_NUDGE_TYPE = "NUDGE_TYPE"
        const val EXTRA_NUDGE_ID = "NUDGE_ID"
        const val EXTRA_PROTOCOL_ID = "PROTOCOL_ID"
    }
}
