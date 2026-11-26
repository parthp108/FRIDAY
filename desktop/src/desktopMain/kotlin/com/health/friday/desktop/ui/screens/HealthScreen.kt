package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.repository.HealthArchiveRecordSummary
import com.health.friday.desktop.ui.components.StatCard
import com.health.friday.shared.models.DailySignalDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopHealthScreen(
    signals: DailySignalDto,
    isPhoneConnected: Boolean,
    healthArchiveRecords: List<HealthArchiveRecordSummary> = emptyList()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Activity", "Heart", "Sleep", "Vitals", "Body")

    val archiveCounts = remember(healthArchiveRecords) {
        healthArchiveRecords.groupingBy { it.recordType }.eachCount().toList().sortedBy { it.first }
    }
    val recentArchiveRecords = remember(healthArchiveRecords) {
        healthArchiveRecords.sortedByDescending { it.startTimeMs ?: it.endTimeMs ?: it.lastModifiedTimeMs }.take(10)
    }
    val archiveDateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    val water = signals.waterIntakeMl
    val activeMins = signals.activeMinutes
    val sleepScore = signals.sleepScore
    val sleepMins = signals.sleepDurationMinutes

    val stepsText = if (signals.stepCount > 0) "${signals.stepCount}" else "—"
    val stepsProgress = if (signals.stepCount > 0) (signals.stepCount / 10000f).coerceIn(0f, 1f) else 0f

    val distKm = if (signals.stepCount > 0) (signals.stepCount * 0.00075f) else 0f
    val distText = if (distKm > 0f) "${(distKm * 100).toInt() / 100f} km" else "—"

    val hrText = if (signals.heartRateBpm > 0) "${signals.heartRateBpm} bpm" else "—"
    val restingHrText = if (signals.restingHeartRate > 0) "${signals.restingHeartRate} bpm" else "—"

    val sleepText = when {
        sleepMins > 0 -> "${sleepMins / 60}h ${sleepMins % 60}m"
        sleepScore != null -> "$sleepScore%"
        else -> "—"
    }

    val activeCalText = if (signals.activeEnergyBurned > 0) "${signals.activeEnergyBurned} kcal" else "—"
    val spO2Text = if (signals.bloodOxygenPercentage > 0f) "${(signals.bloodOxygenPercentage * 10).toInt() / 10f}%" else "—"
    val hrvText = if (signals.hrv > 0f) "${(signals.hrv * 10).toInt() / 10f} ms" else "—"
    val stressText = if (signals.stressScore > 0f) "${signals.stressScore.toInt()} / 100" else "—"
    val recoveryText = if (signals.recoveryScore > 0f) "${signals.recoveryScore.toInt()}%" else "—"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // HEALTH CATEGORY TAB ROW
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(tabs) { index, tabName ->
                val isSelected = selectedTabIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                        .clickable { selectedTabIndex = index }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabName.uppercase(Locale.getDefault()),
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (selectedTabIndex) {
                0 -> {
                    // OVERVIEW
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("STEPS", stepsText, "Goal: 10,000 steps", Color(0xFF4ADE80), stepsProgress, Modifier.weight(1f))
                            StatCard("DISTANCE", distText, "Est. from step stride", Color(0xFF38BDF8), (distKm / 8f).coerceIn(0f, 1f), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("HEART RATE", hrText, "Latest bpm from phone", Color(0xFFEF4444), if (signals.heartRateBpm > 0) (signals.heartRateBpm / 200f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                            StatCard("RESTING HR", restingHrText, "Resting avg from phone", Color(0xFFEF4444), if (signals.restingHeartRate > 0) (signals.restingHeartRate / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("TOTAL SLEEP", sleepText, "Sleep duration & score", Color(0xFFB39DFF), if (sleepMins > 0) (sleepMins / 480f).coerceIn(0f, 1f) else ((sleepScore ?: 0) / 100f).coerceIn(0f, 1f), Modifier.weight(1f))
                            StatCard("ACTIVE WORKOUT", activeMins?.let { "$it mins" } ?: "—", "Exercise duration", Color(0xFF3B82F6), ((activeMins ?: 0) / 60f).coerceIn(0f, 1f), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("HYDRATION", water?.let { "$it ml" } ?: "0 ml", "Goal: 2,500 ml", Color(0xFF38BDF8), ((water ?: 0) / 2500f).coerceIn(0f, 1f), Modifier.weight(1f))
                            StatCard("ACTIVE CALORIES", activeCalText, "Burned today", Color(0xFFF97316), if (signals.activeEnergyBurned > 0) (signals.activeEnergyBurned / 800f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                        }
                    }
                }

                1 -> {
                    // ACTIVITY
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("STEPS", stepsText, "Goal: 10,000", Color(0xFF4ADE80), stepsProgress, Modifier.weight(1f))
                            StatCard("DISTANCE", distText, "Today", Color(0xFF38BDF8), (distKm / 8f).coerceIn(0f, 1f), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("ACTIVE CALORIES", activeCalText, "Burned today", Color(0xFFF97316), if (signals.activeEnergyBurned > 0) (signals.activeEnergyBurned / 800f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                            StatCard("RECOVERY SCORE", recoveryText, "Readiness", Color(0xFF4ADE80), if (signals.recoveryScore > 0f) (signals.recoveryScore / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("ACTIVE TIME", activeMins?.let { "$it mins" } ?: "—", "Exercise duration", Color(0xFF3B82F6), ((activeMins ?: 0) / 60f).coerceIn(0f, 1f), Modifier.weight(1f))
                            StatCard("FLOORS CLIMBED", "—", "Climbed today", Color(0xFF4ADE80), 0f, Modifier.weight(1f))
                        }
                    }
                }

                2 -> {
                    // HEART
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("HEART RATE", hrText, "Latest bpm", Color(0xFFEF4444), if (signals.heartRateBpm > 0) (signals.heartRateBpm / 200f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                            StatCard("RESTING HR", restingHrText, "Resting avg", Color(0xFFEF4444), if (signals.restingHeartRate > 0) (signals.restingHeartRate / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("STRESS INDEX", stressText, "HRV stress metric", Color(0xFFF97316), if (signals.stressScore > 0f) (signals.stressScore / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                            StatCard("RECOVERY SCORE", recoveryText, "Autonomic readiness", Color(0xFF4ADE80), if (signals.recoveryScore > 0f) (signals.recoveryScore / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        StatCard("HEART RATE VARIABILITY (HRV)", hrvText, "RMSSD recovery metric", Color(0xFFEF4444), if (signals.hrv > 0f) (signals.hrv / 100f).coerceIn(0f, 1f) else 0f, Modifier.fillMaxWidth())
                    }
                }

                3 -> {
                    // SLEEP
                    item {
                        StatCard("TOTAL SLEEP", sleepText, "Duration & recovery score", Color(0xFFB39DFF), if (sleepMins > 0) (sleepMins / 480f).coerceIn(0f, 1f) else ((sleepScore ?: 0) / 100f).coerceIn(0f, 1f), Modifier.fillMaxWidth())
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("SLEEP SCORE", sleepScore?.let { "$it%" } ?: "—", "Overall sleep quality", Color(0xFFB39DFF), ((sleepScore ?: 0) / 100f).coerceIn(0f, 1f), Modifier.weight(1f))
                            StatCard("DAYTIME NAPS", "0 naps", "Daytime naps", Color(0xFFB39DFF), 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("SLEEP HR", restingHrText, "Avg sleep HR", Color(0xFFB39DFF), 0f, Modifier.weight(1f))
                            StatCard("SLEEP HRV", hrvText, "Sleep RMSSD", Color(0xFFB39DFF), 0f, Modifier.weight(1f))
                        }
                    }
                }

                4 -> {
                    // VITALS
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("SpO₂ BLOOD OXYGEN", spO2Text, "Blood oxygen %", Color(0xFF38BDF8), if (signals.bloodOxygenPercentage > 0f) (signals.bloodOxygenPercentage / 100f).coerceIn(0f, 1f) else 0f, Modifier.weight(1f))
                            StatCard("BREATHING RATE", "—", "Breaths per min", Color(0xFF38BDF8), 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("BLOOD PRESSURE", "—", "systolic/diastolic mmHg", Color(0xFFEF4444), 0f, Modifier.weight(1f))
                            StatCard("BLOOD GLUCOSE", "—", "mmol/L", Color(0xFFF97316), 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        StatCard("THERMAL GUARD", "36.2 °C", "Normal body temperature", Color(0xFFF97316), 0.36f, Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    // BODY
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("BODY WEIGHT & FAT", "—", "Weight & Fat %", Color(0xFF3B82F6), 0f, Modifier.weight(1f))
                            StatCard("VO₂ MAX", "—", "mL/kg/min", Color(0xFF3B82F6), 0f, Modifier.weight(1f))
                        }
                    }
                    item {
                        StatCard("STRESS INDEX", stressText, "HRV stress level", Color(0xFF38BDF8), if (signals.stressScore > 0f) (signals.stressScore / 100f).coerceIn(0f, 1f) else 0f, Modifier.fillMaxWidth())
                    }
                }
            }

            // CONNECTION STATUS CARD
            item {
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Health Connect & Telemetry Bridge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Health Connect records and telemetry are synced securely through your encrypted Google Drive Vault.",
                            color = Color(0xFFCBD5E1), fontSize = 13.sp
                        )
                    }
                }
            }

            // Encrypted Health Connect archive overview. Only metadata is rendered;
            // full per-record payloads remain in the protected cloud-sync store.
            item {
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Encrypted Health Connect Archive", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "${healthArchiveRecords.size} records across ${archiveCounts.size} record types",
                            color = Color(0xFF94A3B8), fontSize = 12.sp
                        )
                        if (healthArchiveRecords.isEmpty()) {
                            Text("No Health Connect archive records have been synced to this PC yet.", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        } else {
                            archiveCounts.forEach { (recordType, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(recordType, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                    Text(count.toString(), color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = Color(0xFF334155))
                            Text("Recently updated records", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            recentArchiveRecords.forEach { record ->
                                val timestamp = record.startTimeMs ?: record.endTimeMs ?: record.lastModifiedTimeMs
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(record.recordType, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${archiveDateFormat.format(Date(timestamp))}  •  ${record.dataOriginPackage}",
                                        color = Color(0xFF94A3B8), fontSize = 11.sp
                                    )
                                }
                            }
                            Text("Raw record payloads are not displayed in this overview.", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
