package com.health.friday.desktop.features.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.health.friday.desktop.features.telemetry.SkiaStreamRenderer
import com.health.friday.desktop.network.TelemetryClient
import com.health.friday.desktop.ui.components.WidgetContainer

@Composable
fun ZeroLatencyStreamWidget(
    telemetryClient: TelemetryClient,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    WidgetContainer(title = "Live Android Telemetry Stream", modifier = modifier) {
        if (isConnected) {
            // Drop in the isolated Skia Canvas
            SkiaStreamRenderer(
                rawPixelsFlow = telemetryClient.screenPixels,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Stream Offline. Awaiting connection...", color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}
