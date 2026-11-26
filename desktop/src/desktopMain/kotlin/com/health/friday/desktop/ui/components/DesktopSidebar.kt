package com.health.friday.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DesktopNavTab(val title: String, val icon: ImageVector) {
    HOME("Home Dashboard", Icons.Default.Home),
    LOOM("Loom Day Builder", Icons.Default.Timeline),
    HEALTH("Health & Telemetry", Icons.Default.Favorite),
    NUTRITION("Nutrition & Meals", Icons.Default.Restaurant),
    TASKS("Tasks & Checklist", Icons.Default.CheckCircle),
    JOURNAL("Journal & Reflection", Icons.Default.MenuBook),
    ASSISTANT("AI Assistant", Icons.Default.SmartToy),
    DIAGNOSTICS("Diagnostics", Icons.Default.Biotech),
    SETTINGS("Settings & Persona", Icons.Default.Settings)
}

@Composable
fun DesktopSidebar(
    selectedTab: DesktopNavTab,
    isPhoneConnected: Boolean = false,
    onTabSelected: (DesktopNavTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(Color(0xFF131B2B))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Header Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF00E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "FRIDAY",
                        tint = Color(0xFF00363D),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "F.R.I.D.A.Y.",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "System Control Center",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp
                    )
                }
            }

            // Nav Tabs
            DesktopNavTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) Color(0xFF00363D) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color(0xFF00363D) else Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
