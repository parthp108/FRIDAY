package com.health.friday.desktop.features.dashboard

// The immutable state rendered by the UI
data class DashboardState(
    val isSystemConnected: Boolean = false,
    val activeAndroidPackages: List<String> = emptyList(),
    val telemetryPingMs: Int = 0
)

// The actions the UI can send to the ScreenModel
sealed class DashboardIntent {
    object ConnectTelemetry : DashboardIntent()
    object DisconnectTelemetry : DashboardIntent()
    data class ProcessIncomingScreenNode(val domHash: String) : DashboardIntent()
}
