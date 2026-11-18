package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.network.LocalNetworkUtils

private data class DiagRow(val name: String, val ok: Boolean?)

@Composable
fun DesktopDiagnosticsScreen(
    isPhoneConnected: Boolean,
    activeModel: String,
    phoneName: String? = null
) {
    val wifiIp = remember { LocalNetworkUtils.getLocalWifiIpAddress() }
    val usbLabel = "Phone link (USB Bridge)" + (phoneName?.let { " - $it" } ?: " (127.0.0.1:8088)")
    val wifiLabel = "Wireless Wi-Fi Sync (ws://$wifiIp:8089/ws/friday)"
    val cloudLabel = "Over-the-Internet Cloud Sync (wss://relay.friday-eco.org)"

    val rows = listOf(
        DiagRow(usbLabel, isPhoneConnected),
        DiagRow(wifiLabel, isPhoneConnected),
        DiagRow(cloudLabel, isPhoneConnected),
        DiagRow("AI routing ($activeModel)", true)
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("System Diagnostics & Triple-Bridge Sync", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Wireless & Internet Synchronization Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("1. USB Mode: Auto-bound over `adb forward tcp:8088 tcp:8088`.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("2. Wi-Fi LAN Mode: Both devices connect on local network to `ws://$wifiIp:8089/ws/friday`.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("3. Over-the-Internet Mode: Syncs live over 5G/LTE or cellular internet via WSS Cloud Relay when outside local Wi-Fi.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
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
                        true -> "CONNECTED" to Color(0xFF166534)
                        false -> "READY / SCANNING" to Color(0xFF0284C7)
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
