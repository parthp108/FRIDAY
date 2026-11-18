package com.health.friday.execution

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log

class RemoteInputReplyHandler(private val context: Context) {

    companion object {
        private const val TAG = "RemoteInputReplyHandler"
    }

    /**
     * Tier 2: Inspects active notifications from [packageName], locates Action with RemoteInput,
     * and sends direct background text reply without opening app UI.
     */
    fun sendBackgroundReply(
        activeNotifications: Array<StatusBarNotification>?,
        targetPackageName: String,
        replyText: String
    ): ExecutionResult {
        if (activeNotifications == null || activeNotifications.isEmpty()) {
            return ExecutionResult(false, ExecutionTier.TIER_2_REMOTE_INPUT, "No active notifications found.")
        }

        val sbn = activeNotifications.firstOrNull { it.packageName == targetPackageName }
            ?: return ExecutionResult(false, ExecutionTier.TIER_2_REMOTE_INPUT, "No active notification for package $targetPackageName.")

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

        val action = notification.actions?.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.any { remoteInput ->
                remoteInput.resultKey != null
            }
        } ?: return ExecutionResult(false, ExecutionTier.TIER_2_REMOTE_INPUT, "No RemoteInput action found on notification from $targetPackageName.")

        val remoteInput = action.remoteInputs.first { it.resultKey != null }
        val intent = Intent()
        val bundle = Bundle().apply {
            putCharSequence(remoteInput.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        return try {
            action.actionIntent.send(context, 0, intent)
            Log.i(TAG, "Successfully sent background RemoteInput reply to $title ($targetPackageName)")
            ExecutionResult(true, ExecutionTier.TIER_2_REMOTE_INPUT, "Background reply sent successfully to $title.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send RemoteInput reply", e)
            ExecutionResult(false, ExecutionTier.TIER_2_REMOTE_INPUT, "Failed to send reply: ${e.message}")
        }
    }
}
