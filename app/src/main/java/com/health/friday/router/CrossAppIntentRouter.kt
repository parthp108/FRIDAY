package com.health.friday.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.health.friday.bridge.FridayNotificationListener

enum class RouteStatus {
    COMPLETED_HEADLESS,
    REQUIRES_GUI_COMMIT,
    FAILED
}

data class ExecutionRouteResult(
    val status: RouteStatus,
    val summaryMessage: String,
    val targetPackageForGuiCommit: String? = null,
    val guiCommitInstruction: String? = null
)

class CrossAppIntentRouter(
    private val context: Context,
    private val contactResolver: ContactEntityResolver = ContactEntityResolver(context),
    private val appFunctionBridge: AppFunctionBridge = AppFunctionBridge(context)
) {

    /**
     * Routes a cross-app messaging command through Tier 1A -> Tier 1B -> Tier 2/3 -> Tier 4.
     */
    suspend fun routeSendMessage(
        recipientQuery: String,
        messageBody: String,
        appName: String = "whatsapp"
    ): ExecutionRouteResult {
        val targetPkg = when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "telegram" -> "org.telegram.messenger"
            "instagram" -> "com.instagram.android"
            "messages", "sms" -> "com.google.android.apps.messaging"
            else -> "com.whatsapp"
        }

        // TIER 1A: Try Zero-UI Headless Notification RemoteInput Reply (<10ms)
        val sentViaRemoteInput = FridayNotificationListener.sendHeadlessReply(
            context = context,
            recipientName = recipientQuery,
            messageText = messageBody,
            preferredPackage = targetPkg
        )
        if (sentViaRemoteInput) {
            return ExecutionRouteResult(
                status = RouteStatus.COMPLETED_HEADLESS,
                summaryMessage = "Replied to $recipientQuery in the background via Notification RemoteInput."
            )
        }

        // TIER 1B: Try Android 16 AppFunctionManager (<30ms)
        val sentViaAppFunction = appFunctionBridge.tryExecuteAppFunction(
            targetPackage = targetPkg,
            functionIdentifier = "sendMessage",
            stringParams = mapOf("recipient" to recipientQuery, "body" to messageBody)
        )
        if (sentViaAppFunction) {
            return ExecutionRouteResult(
                status = RouteStatus.COMPLETED_HEADLESS,
                summaryMessage = "Sent message to $recipientQuery via Android 16 AppFunctions."
            )
        }

        // TIER 2 + TIER 3: Resolve Contact via Jaro-Winkler & Deep-Link Teleport directly into chat
        val resolvedContact = contactResolver.resolveBestContact(recipientQuery)
        if (targetPkg == "com.whatsapp" && resolvedContact != null) {
            val encodedText = Uri.encode(messageBody)
            val deepLinkUri = Uri.parse(
                "whatsapp://send?phone=${resolvedContact.whatsappDigitsOnly}&text=$encodedText"
            )
            val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return try {
                context.startActivity(intent)
                // Hand off to Phase 7 (Ghost Finger) exclusively to tap the final Send button!
                ExecutionRouteResult(
                    status = RouteStatus.REQUIRES_GUI_COMMIT,
                    summaryMessage = "Opened WhatsApp chat with ${resolvedContact.displayName} pre-filled.",
                    targetPackageForGuiCommit = "com.whatsapp",
                    guiCommitInstruction = "CLICK element with id=send or contentDescription='Send'"
                )
            } catch (e: Exception) {
                ExecutionRouteResult(
                    status = RouteStatus.FAILED,
                    summaryMessage = "WhatsApp is not installed or could not handle deep link."
                )
            }
        }

        // Fallback to Full GUI Navigation (Phase 7) if contact isn't in Phonebook
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            ExecutionRouteResult(
                status = RouteStatus.REQUIRES_GUI_COMMIT,
                summaryMessage = "Launched $appName for full UI search.",
                targetPackageForGuiCommit = targetPkg,
                guiCommitInstruction = "Search for '$recipientQuery', open chat, type '$messageBody', and click Send."
            )
        } else {
            ExecutionRouteResult(
                status = RouteStatus.FAILED,
                summaryMessage = "Target app ($targetPkg) not found on device."
            )
        }
    }

    /**
     * Routes a Ride-Hailing / Navigation intent via Deep-Link URI Teleportation.
     */
    fun routeRideOrNavigation(
        destinationQuery: String,
        provider: String = "uber"
    ): ExecutionRouteResult {
        val encodedDest = Uri.encode(destinationQuery)

        return when (provider.lowercase()) {
            "uber" -> {
                val uberUri = Uri.parse(
                    "uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encodedDest"
                )
                val intent = Intent(Intent.ACTION_VIEW, uberUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ExecutionRouteResult(
                        status = RouteStatus.REQUIRES_GUI_COMMIT,
                        summaryMessage = "Pre-filled Uber ride to $destinationQuery.",
                        targetPackageForGuiCommit = "com.ubercab",
                        guiCommitInstruction = "Select ride tier and stop before final payment confirmation."
                    )
                } catch (e: Exception) {
                    routeRideOrNavigation(destinationQuery, "maps")
                }
            }
            else -> {
                val mapsUri = Uri.parse("google.navigation:q=$encodedDest")
                val intent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionRouteResult(
                    status = RouteStatus.COMPLETED_HEADLESS,
                    summaryMessage = "Started turn-by-turn navigation to $destinationQuery."
                )
            }
        }
    }
}
