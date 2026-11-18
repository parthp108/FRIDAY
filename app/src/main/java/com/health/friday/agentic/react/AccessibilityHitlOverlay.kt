package com.health.friday.agentic.react

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccessibilityHitlOverlay(
    private val service: AccessibilityService
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Suspends the ReAct coroutine and renders a hardware-protected TYPE_ACCESSIBILITY_OVERLAY
     * confirmation banner. Returns true if the user taps "CONFIRM", or false on "ABORT" / timeout.
     */
    suspend fun requestUserConfirmation(
        actionSummary: String,
        targetLabel: String,
        timeoutMs: Long = 12_000L
    ): Boolean = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

            val container = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 32, 40, 32)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F210141D"))
                    cornerRadius = 36f
                    setStroke(3, Color.parseColor("#00E5FF"))
                }
            }

            val titleView = TextView(service).apply {
                text = "FRIDAY // SECURITY GATE (TIER 2 COMMIT)"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            val bodyView = TextView(service).apply {
                text = "$actionSummary\nTarget: \"$targetLabel\""
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 12, 0, 20)
            }

            val buttonRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            var isRemoved = false
            fun dismissOverlay(approved: Boolean) {
                if (isRemoved) return
                isRemoved = true
                runCatching { wm.removeView(container) }
                if (cont.isActive) cont.resume(approved)
            }

            val abortBtn = Button(service).apply {
                text = "ABORT"
                setTextColor(Color.parseColor("#FF5252"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { dismissOverlay(false) }
            }

            val confirmBtn = Button(service).apply {
                text = "CONFIRM"
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E676"))
                    cornerRadius = 20f
                }
                setOnClickListener { dismissOverlay(true) }
            }

            buttonRow.addView(abortBtn)
            buttonRow.addView(confirmBtn)
            container.addView(titleView)
            container.addView(bodyView)
            container.addView(buttonRow)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 96
            }

            runCatching {
                wm.addView(container, params)
            }.onFailure {
                if (cont.isActive) cont.resume(false)
                return@post
            }

            val timeoutRunnable = Runnable { dismissOverlay(false) }
            mainHandler.postDelayed(timeoutRunnable, timeoutMs)

            cont.invokeOnCancellation {
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post { dismissOverlay(false) }
            }
        }
    }
}
