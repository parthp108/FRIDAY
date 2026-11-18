package com.health.friday.desktop.features.telemetry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.health.friday.desktop.network.TelemetryClient
import com.health.friday.desktop.ui.components.TelemetryChart

@Composable
fun DeviceStreamPane(client: TelemetryClient) {
    val rawPixelsState = client.screenPixels.collectAsState()
    val stats by client.telemetryStats.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Active Telemetry Stream",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Frame Render Latency (ms)", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryChart(dataPoints = stats)

        Spacer(modifier = Modifier.height(32.dp))

        // Optimized 60 FPS Skia Canvas Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (rawPixelsState.value != null) {
                OptimizedStreamSurface(rawPixelsState = rawPixelsState)
            } else {
                Text(
                    text = "Awaiting HardwareBuffer byte stream...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
