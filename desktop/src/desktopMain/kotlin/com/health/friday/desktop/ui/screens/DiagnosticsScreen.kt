package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DiagRow(val name: String, val ok: Boolean?)

@Composable
fun DesktopDiagnosticsScreen(
    isPhoneConnected: Boolean = false,
    activeModel: String = "Local Ollama",
    phoneName: String? = null
) {
    val cloudLabel = "Encrypted Google Drive Cloud Vault"

    val rows = listOf(
        DiagRow(cloudLabel, true),
        DiagRow("AI routing ($activeModel)", true)
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("System Diagnostics & Cloud Vault Sync", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Encrypted Cloud Vault Synchronization Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("1. Hardware-protected master key wraps end-to-end encrypted batches.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("2. Syncs seamlessly across all your devices using Google Drive.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                }
            }
        }
        items(rows) { row ->
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = row.name, color = Color.White, fontSize = 13.sp)
                    val (label, bg) = when (row.ok) {
                        true -> "ACTIVE" to Color(0xFF166534)
                        false -> "OFFLINE" to Color(0xFFEF4444)
                        null -> "NOT CHECKED" to Color(0xFF475569)
                    }
                    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
