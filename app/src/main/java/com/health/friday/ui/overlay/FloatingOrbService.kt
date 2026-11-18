package com.health.friday.ui.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.health.friday.FridayApplication
import com.health.friday.MainActivity
import com.health.friday.R
import com.health.friday.os.bridge.FridayAccessibilityService
import com.health.friday.proactive.ProactiveVoiceActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class FloatingOrbService : Service() {

    companion object {
        private const val TAG = "FloatingOrbService"
        private const val NOTIFICATION_ID = 9002
        private const val CHANNEL_ID = "friday_overlay_channel"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingOrbService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOrbService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    // Layout views & state
    private var isExpanded = false
    private var collapsedOrbView: View? = null
    private var expandedHudView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var isDragging = false

    // Telemetry text views inside expanded HUD
    private var txtCalories: TextView? = null
    private var txtWater: TextView? = null
    private var txtSteps: TextView? = null
    private var txtHr: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        if (Settings.canDrawOverlays(this)) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupOverlayWindows()
            startTelemetryUpdates()
        } else {
            stopSelf()
        }
    }

    private fun setupOverlayWindows() {
        val wm = windowManager ?: return

        val density = resources.displayMetrics.density
        val orbSizePx = (56 * density).toInt()

        val params = WindowManager.LayoutParams(
            orbSizePx,
            orbSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 500
        }

        // 1. Sleek Circular Cyan Floating Orb
        collapsedOrbView = createCircularOrbView(density)

        collapsedOrbView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    wm.updateViewLayout(collapsedOrbView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                        expandHudOverlay(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(collapsedOrbView, params)
            Log.i(TAG, "Circular Floating Telemetry Orb added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add collapsed orb view", e)
        }
    }

    private fun createCircularOrbView(density: Float): View {
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xEE0F141C.toInt())
            setStroke((2 * density).toInt(), 0xFF5CC8FF.toInt())
        }

        return TextView(this).apply {
            text = "⚡"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF5CC8FF.toInt())
            background = backgroundDrawable
            elevation = 16f
        }
    }

    private fun expandHudOverlay(anchorX: Int, anchorY: Int) {
        val wm = windowManager ?: return
        if (isExpanded) return

        val density = resources.displayMetrics.density
        val hudWidth = (320 * density).toInt()

        val hudParams = WindowManager.LayoutParams(
            hudWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX.coerceAtLeast(30)
            y = anchorY.coerceAtLeast(100)
        }

        expandedHudView = createExpandedHudView(density)

        try {
            wm.addView(expandedHudView, hudParams)
            collapsedOrbView?.visibility = View.GONE
            isExpanded = true
            Log.i(TAG, "Expanded Telemetry HUD popup displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to expand HUD overlay", e)
        }
    }

    private fun collapseHudOverlay() {
        val wm = windowManager ?: return
        if (!isExpanded) return

        if (expandedHudView != null) {
            try {
                wm.removeView(expandedHudView)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove expanded HUD view", e)
            }
            expandedHudView = null
        }

        collapsedOrbView?.visibility = View.VISIBLE
        isExpanded = false
    }

    private fun createExpandedHudView(density: Float): View {
        val cardBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            setColor(0xF00F141C.toInt())
            setStroke((2 * density).toInt(), 0xFF1A222D.toInt())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            elevation = 20f
        }

        // Header Row: Title + Close Button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "⚡ FRIDAY TELEMETRY"
            setTextColor(0xFF5CC8FF.toInt())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 16f
            setOnClickListener { collapseHudOverlay() }
        }

        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        container.addView(headerRow)

        // Telemetry Metrics List
        txtCalories = createMetricRow(container, "🍏 Calories:", "Loading...")
        
        val waterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        txtWater = TextView(this).apply {
            text = "💧 Water: Loading..."
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addWaterBtn = Button(this).apply {
            text = "+250ml"
            setTextColor(0xFF0F141C.toInt())
            setBackgroundColor(0xFF5CC8FF.toInt())
            textSize = 11f
            setOnClickListener {
                serviceScope.launch {
                    val appContainer = (applicationContext as? FridayApplication)?.container
                    appContainer?.waterRepository?.addWater(250)
                }
            }
        }
        waterRow.addView(txtWater)
        waterRow.addView(addWaterBtn)
        container.addView(waterRow)

        txtSteps = createMetricRow(container, "👟 Steps:", "Loading...")
        txtHr = createMetricRow(container, "❤️ Heart Rate:", "Loading...")

        // Action Buttons Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        val assistantBtn = Button(this).apply {
            text = "🎙 Open Assistant"
            setTextColor(0xFF0F141C.toInt())
            setBackgroundColor(0xFF5CC8FF.toInt())
            textSize = 12f
            setOnClickListener {
                collapseHudOverlay()
                openFridayAssistant()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val lockBtn = Button(this).apply {
            text = "🔒 Lock Screen"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF222B38.toInt())
            textSize = 12f
            setOnClickListener {
                collapseHudOverlay()
                FridayAccessibilityService.lockScreen()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        actionsRow.addView(assistantBtn)
        actionsRow.addView(lockBtn)
        container.addView(actionsRow)

        return container
    }

    private fun createMetricRow(parent: LinearLayout, label: String, value: String): TextView {
        val row = TextView(this).apply {
            text = "$label $value"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
        parent.addView(row)
        return row
    }

    private fun startTelemetryUpdates() {
        serviceScope.launch {
            while (true) {
                try {
                    val appContainer = (applicationContext as? FridayApplication)?.container
                    if (appContainer != null) {
                        val todayStr = LocalDate.now().toString()
                        val todaySignals = withContext(Dispatchers.IO) {
                            appContainer.signalsDao.getSignalForDate(todayStr)
                        }
                        val calories = todaySignals?.kcal ?: 0
                        val water = todaySignals?.waterMl ?: 0
                        val steps = todaySignals?.steps ?: 0
                        val hr = todaySignals?.restingHr ?: 0

                        txtCalories?.text = "🍏 Calories: $calories / 2500 kcal"
                        txtWater?.text = "💧 Water: $water ml"
                        txtSteps?.text = "👟 Steps: ${String.format("%,d", steps)}"
                        txtHr?.text = "❤️ Heart Rate: ${if (hr > 0) "$hr bpm" else "-- bpm"}"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Telemetry update failed", e)
                }
                delay(5000)
            }
        }
    }

    private fun openFridayAssistant() {
        try {
            ProactiveVoiceActivity.launch(
                this,
                "FRIDAY online. How can I help you?",
                "assistant_overlay"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch FRIDAY Translucent Overlay Assistant", e)
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FRIDAY Floating Telemetry",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FRIDAY Telemetry Overlay")
            .setContentText("Floating telemetry active")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        collapseHudOverlay()
        if (collapsedOrbView != null) {
            try {
                windowManager?.removeView(collapsedOrbView)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove collapsed orb view", e)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
