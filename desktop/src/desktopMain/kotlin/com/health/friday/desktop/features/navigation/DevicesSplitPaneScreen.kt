package com.health.friday.desktop.features.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.health.friday.desktop.features.telemetry.DeviceStreamPane
import com.health.friday.desktop.network.TelemetryClient
import com.health.friday.desktop.ui.components.FridayActionCard

@Composable
fun DevicesSplitPaneScreen() {
    val activeClient = remember { TelemetryClient("127.0.0.1") }

    LaunchedEffect(Unit) {
        activeClient.connect()
    }

    DisposableEffect(Unit) {
        onDispose { activeClient.close() }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Master Pane (Left Side - Fixed Width)
        FridayActionCard(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connected Devices", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Future: List of devices (e.g., "Pixel 8 Pro - Active")
                Text("Pixel 8 Pro (Active)", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Detail Pane (Right Side - Expands to fill)
        FridayActionCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            DeviceStreamPane(client = activeClient)
        }
    }
}
