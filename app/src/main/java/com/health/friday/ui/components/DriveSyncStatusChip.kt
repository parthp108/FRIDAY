package com.health.friday.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.sync.cloud.DriveSyncState
import com.health.friday.sync.cloud.DriveSyncStatus
import com.health.friday.sync.drive.DriveConnectionActivity

private fun relativeTime(ms: Long): String {
    if (ms <= 0L) return "Never"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}

@Composable
fun DriveSyncStatusChip(status: DriveSyncStatus) {
    val context = LocalContext.current
    val (icon, label, tint) = when (status.state) {
        DriveSyncState.SUCCESS -> Triple("✓", relativeTime(status.lastSyncTimeMs), Color(0xFF4ADE80))
        DriveSyncState.SYNCING -> Triple("↑", "Syncing...", Color(0xFF38BDF8))
        DriveSyncState.AUTH_REQUIRED -> Triple("!", "Reconnect Drive", Color(0xFFFBBF24))
        DriveSyncState.FAILED -> Triple("✗", "Sync failed", Color(0xFFF87171))
        DriveSyncState.IDLE -> if (status.lastSyncTimeMs > 0) Triple("✓", relativeTime(status.lastSyncTimeMs), Color(0xFF4ADE80)) else return
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable {
                context.startActivity(Intent(context, DriveConnectionActivity::class.java))
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
