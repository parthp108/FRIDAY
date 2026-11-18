package com.health.friday.os.core

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FridayCoreService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}