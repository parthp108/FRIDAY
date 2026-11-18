package com.health.friday.desktop.features.dashboard

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardScreenModel : StateScreenModel<DashboardState>(DashboardState()) {

    fun processIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.ConnectTelemetry -> startTelemetryStream()
            is DashboardIntent.DisconnectTelemetry -> stopTelemetryStream()
            is DashboardIntent.ProcessIncomingScreenNode -> { /* Handle DOM hash */ }
        }
    }

    private fun startTelemetryStream() {
        screenModelScope.launch {
            // Simulate WebSocket connection delay
            delay(500)
            mutableState.value = state.value.copy(
                isSystemConnected = true,
                telemetryPingMs = 12
            )
        }
    }

    private fun stopTelemetryStream() {
        mutableState.value = state.value.copy(isSystemConnected = false, telemetryPingMs = 0)
    }
}
