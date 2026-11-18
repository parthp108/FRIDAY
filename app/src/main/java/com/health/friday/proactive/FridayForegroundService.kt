package com.health.friday.proactive

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.health.friday.FridayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FridayForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        initAmbientAudioMesh()
    }

    private fun startForegroundNotification() {
        val channelId = "friday_ambient_channel"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "F.R.I.D.A.Y. Ambient OS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous ambient voice mesh listening for 'Hey Friday'"
            }
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("F.R.I.D.A.Y. Ambient AI OS")
            .setContentText("Listening for 'Hey Friday'...")
            .setSmallIcon(R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        }
    }

    private fun initAmbientAudioMesh() {
        val app = applicationContext as? FridayApplication
        val container = app?.container ?: return

        val pipeline = container.ambientAudioPipeline
        val ttsEngine = container.ttsEngine
        val orchestrator = container.aiOrchestrator

        pipeline.startAmbientMesh()

        // 1. Listen for Wakeword ("Hey Friday" / "Friday")
        serviceScope.launch {
            pipeline.wakewordFlow.collect {
                // Background wakeword acknowledged
            }
        }

        // 2. Listen for transcribed user spoken commands
        serviceScope.launch {
            pipeline.transcriptionFlow.collectLatest { transcribedQuery ->
                if (transcribedQuery.isNotBlank() && !transcribedQuery.contains("Audio Stream Received", ignoreCase = true)) {
                    val response = orchestrator.sendMessage(transcribedQuery, section = "ambient_mesh")
                    ttsEngine.speak(response)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        val app = applicationContext as? FridayApplication
        app?.container?.ambientAudioPipeline?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
