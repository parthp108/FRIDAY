package com.health.friday.telemetry

import android.content.Context

object HealthSyncManager {
    fun startPeriodicSync(context: Context) {
        HealthTelemetryWorker.schedulePeriodic(context)
    }
}