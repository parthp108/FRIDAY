package com.health.friday.execution

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class DeepLinkTeleporter(private val context: Context) {

    companion object {
        private const val TAG = "DeepLinkTeleporter"
    }

    /**
     * Tier 3: Teleports directly to specific app screen via Deep-Link URI or explicit Intent action.
     */
    fun teleport(packageName: String, deepLinkUri: String?, actionIntentName: String? = null): ExecutionResult {
        return try {
            val intent = if (!deepLinkUri.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri)).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else if (!actionIntentName.isNullOrBlank()) {
                Intent(actionIntentName).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } ?: return ExecutionResult(false, ExecutionTier.TIER_3_DEEP_LINK, "No launch intent found for $packageName.")
            }

            context.startActivity(intent)
            Log.i(TAG, "Teleported to package $packageName via deep link / intent.")
            ExecutionResult(true, ExecutionTier.TIER_3_DEEP_LINK, "Successfully teleported to $packageName.")
        } catch (e: Exception) {
            Log.e(TAG, "Deep link teleportation failed", e)
            ExecutionResult(false, ExecutionTier.TIER_3_DEEP_LINK, e.message ?: "Teleportation error")
        }
    }
}
