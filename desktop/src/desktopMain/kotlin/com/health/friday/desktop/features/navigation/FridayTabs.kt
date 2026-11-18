package com.health.friday.desktop.features.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.health.friday.desktop.features.dashboard.DashboardScreen

object DashboardTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Dashboard)
            return remember { TabOptions(index = 0u, title = "Dashboard", icon = icon) }
        }

    @Composable
    override fun Content() {
        DashboardScreen().Content()
    }
}

object DevicesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.DeveloperBoard)
            return remember { TabOptions(index = 1u, title = "Devices", icon = icon) }
        }

    @Composable
    override fun Content() {
        DevicesSplitPaneScreen()
    }
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Settings)
            return remember { TabOptions(index = 2u, title = "Settings", icon = icon) }
        }

    @Composable
    override fun Content() {
        // Placeholder for future settings
    }
}
