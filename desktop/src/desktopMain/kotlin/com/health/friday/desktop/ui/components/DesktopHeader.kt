package com.health.friday.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopHeader(
    selectedTabTitle: String,
    personaStyle: String,
    activeModel: String,
    onAddWater: (Int) -> Unit,
    onOpenAssistant: () -> Unit = {},
    onToggleHudMode: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = selectedTabTitle,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date()) + " • Persona: $personaStyle • $activeModel",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenAssistant,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF00363D)),
                    modifier = Modifier.height(36.dp).padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assistant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onAddWater(250) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF131B2B), contentColor = Color(0xFF00E5FF)),
                    modifier = Modifier.height(36.dp).padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+250ml Water", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onToggleHudMode,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("HUD Overlay", fontSize = 11.sp)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF131B2B), modifier = Modifier.padding(bottom = 16.dp))
    }
}
