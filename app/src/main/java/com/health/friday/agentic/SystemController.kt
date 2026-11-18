package com.health.friday.agentic

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log

open class SystemController(private val context: Context) {

    open fun hasDndAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** Opens Android's "Do Not Disturb access" screen */
    fun openDndAccessSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open DND settings", e)
        false
    }

    /** Opens Alarms & Reminders settings screen */
    fun openAlarmSettings(): Boolean = try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open Alarm settings", e)
        false
    }

    /** Opens Turn Screen On settings screen */
    fun openTurnScreenOnSettings(): Boolean = try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent("android.settings.MANAGE_TURN_SCREEN_ON", Uri.parse("package:" + context.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open Turn Screen On settings", e)
        false
    }

    /** Opens OxygenOS / Android App Info page (Auto-launch, Battery usage) */
    fun openAppDetailsSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open App Details settings", e)
        false
    }

    /** Opens Display Over Other Apps / Pop-up Window settings */
    fun openOverlaySettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open Overlay settings", e)
        false
    }

    /** Opens Full Screen Intent settings (Android 14/15/16) */
    fun openFullScreenIntentSettings(): Boolean = try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open Full Screen Intent settings", e)
        false
    }

    /** Opens Device & App Notifications / Notification Access screen */
    fun openNotificationListenerSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open Notification Listener settings", e)
        false
    }

    /** Opens FRIDAY Notification Channel & Live Alerts settings */
    fun openAppNotificationSettings(): Boolean = try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("FRIDAY_AGENT", "Could not open App Notification settings", e)
        false
    }

    open fun toggleDoNotDisturb(enable: Boolean): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e("FRIDAY_AGENT", "DND access not granted.")
            return false
        }

        return try {
            notificationManager.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (e: Exception) {
            Log.e("FRIDAY_AGENT", "Failed to toggle DND", e)
            false
        }
    }

    open fun setTimer(minutes: Int, message: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("FRIDAY_AGENT", "Timer could not be started", e)
            false
        }
    }
}
