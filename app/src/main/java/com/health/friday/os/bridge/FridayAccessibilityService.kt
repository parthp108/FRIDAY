package com.health.friday.os.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.health.friday.MainActivity
import com.health.friday.assistant.FridayVoiceInteractionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class FridayAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: FridayAccessibilityService? = null

        fun getInstance(): FridayAccessibilityService? = instance

        fun lockScreen(): Boolean {
            val svc = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else false
        }
    }

    // Maps distilled element IDs [1..N] to live view references for deterministic clicking
    private val activeNodeMap = ConcurrentHashMap<Int, AccessibilityNodeInfo>()
    private var lastEventTimestamp = System.currentTimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventTimestamp = System.currentTimeMillis()
    }

    /**
     * Awaits UI stabilization (no new accessibility content events for quietPeriodMs).
     */
    suspend fun awaitUiStabilization(quietPeriodMs: Long = 250L, timeoutMs: Long = 3500L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val quietDuration = System.currentTimeMillis() - lastEventTimestamp
            if (quietDuration >= quietPeriodMs) {
                return
            }
            delay(50L)
        }
    }

    override fun onInterrupt() {
        // Handle interruptions
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        val action = event.action
        val keyCode = event.keyCode

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_SEARCH ||
                (keyCode == KeyEvent.KEYCODE_HOME && event.isLongPress)
            ) {
                triggerLiveAssistant()
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerLiveAssistant() {
        runCatching {
            FridayVoiceInteractionService.triggerAmbientSession("Live Assistant")
        }
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_ASSIST
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_assistant", true)
            }
            startActivity(intent)
        }
    }

    /**
     * Traverses the active window hierarchy, strips out non-semantic containers,
     * and builds a token-efficient DOM representation for the VLM.
     */
    fun captureDistilledDom(): String {
        val root = rootInActiveWindow ?: return ""
        activeNodeMap.clear()

        val sb = StringBuilder()
        var currentIndex = 1

        fun traverse(node: AccessibilityNodeInfo?) {
            node ?: return
            if (!node.isVisibleToUser) return

            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val isInteractive = node.isClickable || node.isEditable || node.isScrollable
            val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

            // Keep only actionable or information-bearing nodes with positive area
            if ((hasText || isInteractive) && bounds.width() > 0 && bounds.height() > 0) {
                val id = currentIndex++
                activeNodeMap[id] = node
                
                val flags = buildList {
                    if (node.isClickable) add("CLICK")
                    if (node.isEditable) add("EDIT")
                    if (node.isScrollable) add("SCROLL")
                }.joinToString(",")

                val label = node.text ?: node.contentDescription ?: ""
                sb.appendLine("[$id] ${node.className?.toString()?.substringAfterLast('.')} id=${node.viewIdResourceName?.substringAfterLast('/')} ($flags) \"$label\"")
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return sb.toString()
    }

    /**
     * Captures the current screen bitmap using AccessibilityService takeScreenshot API.
     */
    suspend fun captureScreenBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, true)
                        screenshot.hardwareBuffer.close()
                        continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("FridayAccessibility", "Failed to take screenshot: errorCode=$errorCode")
                        continuation.resume(null)
                    }
                }
            )
        } else {
            continuation.resume(null)
        }
    }

    /**
     * Executes deterministic UI actions passed down from the Orchestrator LLM.
     */
    fun executeAction(elementId: Int, action: String): Boolean {
        val node = activeNodeMap[elementId] ?: return false
        val bounds = Rect().also { node.getBoundsInScreen(it) }

        return when (action) {
            "CLICK" -> {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    // Fallback to geometric physical gesture synthesis if app blocks standard API
                    dispatchCoordinateTap(bounds.exactCenterX(), bounds.exactCenterY())
                } else true
            }
            "SCROLL" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            else -> false
        }
    }

    private fun dispatchCoordinateTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
