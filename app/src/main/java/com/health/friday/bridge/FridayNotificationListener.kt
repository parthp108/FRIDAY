package com.health.friday.bridge

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class CachedReplyHandle(
    val packageName: String,
    val senderTitle: String,
    val lastMessageText: String,
    val timestamp: Long,
    val actionIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

class FridayNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "FRIDAY_NOTIF_BRIDGE"

        // Maps normalized sender name (e.g. "mom", "rahul") -> CachedReplyHandle
        private val replyHandleCache = ConcurrentHashMap<String, CachedReplyHandle>()

        /**
         * Attempts to reply to a contact completely headlessly in the background (<10ms)
         * using a cached Notification RemoteInput PendingIntent.
         */
        fun sendHeadlessReply(
            context: android.content.Context,
            recipientName: String,
            messageText: String,
            preferredPackage: String? = null
        ): Boolean {
            val normalizedTarget = recipientName.trim().lowercase()

            // Find best matching cached conversation handle
            val handle = replyHandleCache.entries
                .filter { (_, v) ->
                    preferredPackage == null || v.packageName.equals(preferredPackage, ignoreCase = true)
                }
                .firstOrNull { (key, _) ->
                    key.contains(normalizedTarget) || normalizedTarget.contains(key)
                }?.value ?: return false

            return try {
                val fillInIntent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                val resultsBundle = Bundle()
                for (remoteInput in handle.remoteInputs) {
                    resultsBundle.putCharSequence(remoteInput.resultKey, messageText)
                }
                RemoteInput.addResultsToIntent(handle.remoteInputs, fillInIntent, resultsBundle)
                handle.actionIntent.send(context, 0, fillInIntent)

                Log.i(TAG, "Headless reply dispatched to '${handle.senderTitle}' via ${handle.packageName}")
                true
            } catch (e: PendingIntent.CanceledException) {
                // Notification PendingIntent expired or was dismissed; evict from cache
                replyHandleCache.remove(normalizedTarget)
                Log.w(TAG, "Cached PendingIntent expired for $recipientName, falling back to Tier 3.")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch headless reply", e)
                false
            }
        }

        fun getRecentConversations(): List<CachedReplyHandle> {
            return replyHandleCache.values.sortedByDescending { it.timestamp }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        // Inspect notification actions for RemoteInput (Direct Reply capability)
        val actions = notification.actions ?: return
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            val actionIntent = action.actionIntent
            if (!remoteInputs.isNullOrEmpty() && actionIntent != null) {
                val key = title.lowercase()
                replyHandleCache[key] = CachedReplyHandle(
                    packageName = sbn.packageName,
                    senderTitle = title,
                    lastMessageText = text,
                    timestamp = sbn.postTime,
                    actionIntent = actionIntent,
                    remoteInputs = remoteInputs
                )
                Log.d(TAG, "Cached headless RemoteInput handle for '$title' (${sbn.packageName})")
                break
            }
        }
    }
}
