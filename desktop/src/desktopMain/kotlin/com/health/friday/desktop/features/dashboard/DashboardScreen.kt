package com.health.friday.desktop.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.model.rememberScreenModel
import com.health.friday.desktop.ui.components.FridayActionCard
import com.health.friday.desktop.ui.components.FridayButton

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { DashboardScreenModel() }
        val state by screenModel.state.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            FridayActionCard(
                modifier = Modifier.align(Alignment.Center).size(400.dp, 200.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FRIDAY ECo Telemetry",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Status: ${if (state.isSystemConnected) "Connected (${state.telemetryPingMs}ms)" else "Disconnected"}",
                        color = if (state.isSystemConnected) 
                            androidx.compose.material3.MaterialTheme.colorScheme.primary 
                        else 
                            androidx.compose.material3.MaterialTheme.colorScheme.error
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FridayButton(
                            text = if (state.isSystemConnected) "Disconnect" else "Initialize Stream",
                            onClick = {
                                if (state.isSystemConnected) {
                                    screenModel.processIntent(DashboardIntent.DisconnectTelemetry)
                                } else {
                                    screenModel.processIntent(DashboardIntent.ConnectTelemetry)
                                }
                            },
                            isDestructive = state.isSystemConnected
                        )
                    }
                }
            }
        }
    }
}
