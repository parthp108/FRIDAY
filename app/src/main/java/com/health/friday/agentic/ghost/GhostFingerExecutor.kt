package com.health.friday.agentic.ghost

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.health.friday.os.bridge.FridayAccessibilityService
import com.health.friday.vision.grounding.GroundedScreenState
import com.health.friday.vision.grounding.GroundedUiElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GhostFingerExecutor(
    private val service: FridayAccessibilityService
) {
    companion object {
        private const val TAG = "FRIDAY_GHOST_FINGER"
    }

    /**
     * Executes a single grounded GUI command and awaits UI stabilization before returning.
     */
    suspend fun executeCommand(
        command: GuiActionCommand,
        screenState: GroundedScreenState
    ): Boolean {
        val element: GroundedUiElement? = command.targetElementId?.let { screenState.elements[it] }
        val metrics = service.resources.displayMetrics

        val success = when (command.action) {
            GuiActionType.CLICK -> {
                if (element == null) return false
                performClickWithFallback(element, isLongClick = false)
            }

            GuiActionType.LONG_CLICK -> {
                if (element == null) return false
                performClickWithFallback(element, isLongClick = true)
            }

            GuiActionType.SET_TEXT -> {
                if (element == null || command.textInput == null) return false
                performDirectTextInjection(element, command.textInput, pressEnterAfter = false)
            }

            GuiActionType.SET_TEXT_AND_ENTER -> {
                if (element == null || command.textInput == null) return false
                performDirectTextInjection(element, command.textInput, pressEnterAfter = true)
            }

            GuiActionType.SCROLL_DOWN,
            GuiActionType.SCROLL_UP,
            GuiActionType.SWIPE_LEFT,
            GuiActionType.SWIPE_RIGHT -> {
                performScrollWithFallback(
                    element = element,
                    direction = command.action,
                    screenWidth = metrics.widthPixels,
                    screenHeight = metrics.heightPixels
                )
            }

            GuiActionType.NAVIGATE_BACK -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            GuiActionType.NAVIGATE_HOME -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }

            GuiActionType.WAIT_FOR_IDLE -> {
                delay(600L)
                true
            }

            GuiActionType.GOAL_COMPLETE,
            GuiActionType.ABORT_ERROR -> true
        }

        if (success) {
            service.awaitUiStabilization(quietPeriodMs = 250L, timeoutMs = 3500L)
        }
        return success
    }

    private suspend fun performClickWithFallback(
        element: GroundedUiElement,
        isLongClick: Boolean
    ): Boolean {
        val actionConstant = if (isLongClick) {
            AccessibilityNodeInfo.ACTION_LONG_CLICK
        } else {
            AccessibilityNodeInfo.ACTION_CLICK
        }

        var candidateNode = element.liveNodeRef
        var depth = 0
        while (candidateNode != null && depth < 3) {
            if (candidateNode.performAction(actionConstant)) {
                Log.d(TAG, "Semantic click succeeded on element [${element.id}] (depth=$depth)")
                return true
            }
            candidateNode = candidateNode.parent
            depth++
        }

        Log.d(TAG, "Semantic click rejected for [${element.id}]; synthesizing hardware tap.")
        val gesture = GesturePhysicsEngine.buildTapGesture(element.boundsInScreen, isLongClick)
        return dispatchHardwareGesture(gesture)
    }

    private suspend fun performDirectTextInjection(
        element: GroundedUiElement,
        text: String,
        pressEnterAfter: Boolean
    ): Boolean {
        val node = element.liveNodeRef
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val injected = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (injected) {
                if (pressEnterAfter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    delay(80L)
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
                return true
            }
        }

        performClickWithFallback(element, isLongClick = false)
        delay(250L)
        val focusedNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val ok = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok && pressEnterAfter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                delay(80L)
                focusedNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            return ok
        }
        return false
    }

    private suspend fun performScrollWithFallback(
        element: GroundedUiElement?,
        direction: GuiActionType,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val node = element?.liveNodeRef
        if (node != null && node.isScrollable) {
            val scrollAction = when (direction) {
                GuiActionType.SCROLL_DOWN, GuiActionType.SWIPE_LEFT ->
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                GuiActionType.SCROLL_UP, GuiActionType.SWIPE_RIGHT ->
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (node.performAction(scrollAction)) return true
        }

        val gesture = GesturePhysicsEngine.buildBezierScrollGesture(screenWidth, screenHeight, direction)
        return dispatchHardwareGesture(gesture)
    }

    private suspend fun dispatchHardwareGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }
}
