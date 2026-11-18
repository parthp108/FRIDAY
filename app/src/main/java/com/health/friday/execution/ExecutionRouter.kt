package com.health.friday.execution

import android.service.notification.StatusBarNotification
import com.health.friday.os.bridge.FridayAccessibilityService

class ExecutionRouter(
    private val appFunctionExecutor: AppFunctionExecutor,
    private val remoteInputReplyHandler: RemoteInputReplyHandler,
    private val deepLinkTeleporter: DeepLinkTeleporter
) {

    /**
     * Executes an action by traversing the 4 Execution Tiers in priority order:
     * Tier 1: AppFunctions
     * Tier 2: Headless RemoteInput Notification Reply
     * Tier 3: Deep-Link URI Teleportation
     * Tier 4: Accessibility Node / Gesture Fallback
     */
    suspend fun routeAndExecute(
        request: ExecutionRequest,
        activeNotifications: Array<StatusBarNotification>?,
        accessibilityService: FridayAccessibilityService?,
        replyText: String? = null
    ): ExecutionResult {

        // Tier 1: Try AppFunctions API
        val tier1Result = appFunctionExecutor.executeAppFunction(request)
        if (tier1Result.success) return tier1Result

        // Tier 2: Try RemoteInput Background Notification Reply if text payload exists
        if (!replyText.isNullOrBlank()) {
            val tier2Result = remoteInputReplyHandler.sendBackgroundReply(
                activeNotifications,
                request.packageName,
                replyText
            )
            if (tier2Result.success) return tier2Result
        }

        // Tier 3: Try Deep-Link Teleportation
        val deepLinkUri = request.queryParams["deeplink"]
        val tier3Result = deepLinkTeleporter.teleport(
            request.packageName,
            deepLinkUri,
            request.queryParams["intent_action"]
        )
        if (tier3Result.success) return tier3Result

        // Tier 4: Accessibility Node / Gesture Fallback
        if (accessibilityService != null && request.targetElementId != null) {
            val clicked = accessibilityService.executeAction(request.targetElementId, request.actionName)
            if (clicked) {
                return ExecutionResult(
                    true,
                    ExecutionTier.TIER_4_ACCESSIBILITY_FALLBACK,
                    "Successfully executed Accessibility action ${request.actionName} on element ID ${request.targetElementId}."
                )
            }
        }

        return ExecutionResult(
            false,
            ExecutionTier.TIER_4_ACCESSIBILITY_FALLBACK,
            "All 4 execution tiers failed for action ${request.actionName}."
        )
    }
}
