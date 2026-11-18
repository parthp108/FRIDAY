package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.repository.todayMeals
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayMealDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private val LoomBg = Color(0xFF090D12)
private val LoomSurface = Color(0xFF111821)
private val LoomCyan = Color(0xFF55E6F2)
private val LoomPurple = Color(0xFFB39DFF)
private val LoomGreen = Color(0xFF75D6A0)
private val LoomAmber = Color(0xFFFFC477)
private val LoomPink = Color(0xFFFF85A1)
private val LoomMuted = Color(0xFF8796A5)
private val LoomGrid = Color(0xFF273440)

enum class LoomZoom(val hourWidthDp: Float, val label: String) {
    FULL_DAY(40f, "24h"),
    STANDARD(80f, "12h"),
    FOCUS(160f, "4h")
}

data class LoomTimelineEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val lane: String,
    val startHour: Float,
    val durationHours: Float,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun DesktopLoomScreen(signals: DailySignalDto, meals: List<FridayMealDto> = emptyList()) {
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var zoomMode by remember { mutableStateOf(LoomZoom.STANDARD) }
    var selectedEvent by remember { mutableStateOf<LoomTimelineEvent?>(null) }

    val isToday = currentDate == LocalDate.now()
    val dateLabel = currentDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))

    val currentHourFloat = remember {
        val now = LocalTime.now()
        now.hour + now.minute / 60f
    }

    val todayMeals = remember(meals) { meals.todayMeals() }

    val timelineEvents = remember(todayMeals, signals) {
        buildList {
            // NUTRITION LANE
            todayMeals.forEach { m ->
                val c = Calendar.getInstance().apply { timeInMillis = m.timestamp }
                val hour = (c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f).coerceAtMost(23.5f)
                add(LoomTimelineEvent(m.id, m.name, "${m.calories} kcal", "NUTRITION", hour, 0.6f, LoomCyan, Icons.Default.Restaurant))
            }
            if (signals.waterIntakeMl != null && signals.waterIntakeMl!! > 0) {
                add(LoomTimelineEvent("water_1", "Hydration", "${signals.waterIntakeMl} ml", "NUTRITION", 10f, 0.5f, LoomCyan, Icons.Default.WaterDrop))
            }

            // ACTIVITY LANE
            if (signals.activeMinutes != null && signals.activeMinutes!! > 0) {
                add(LoomTimelineEvent("workout_1", "Active Workout", "${signals.activeMinutes} mins", "ACTIVITY", 17f, 1.0f, LoomGreen, Icons.Default.FitnessCenter))
            }

            // MIND / SCREEN TIME LANE
            if (signals.phoneUsageMinutes != null && signals.phoneUsageMinutes!! > 0) {
                add(LoomTimelineEvent("screen_1", "Phone Usage", "${signals.phoneUsageMinutes} mins screen time", "MIND", 14f, 1.2f, LoomPink, Icons.Default.Phonelink))
            }

            // REST LANE
            if (signals.sleepScore != null && signals.sleepScore!! > 0) {
                add(LoomTimelineEvent("sleep_1", "Sleep Recovery", "Score ${signals.sleepScore}%", "REST", 0f, 7f, LoomPurple, Icons.Default.NightsStay))
            }
        }
    }

    val hourWidthPx = zoomMode.hourWidthDp
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoomBg)
    ) {
        // HEADER & DATE SWITCHER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "The Loom — Day Builder & Spatio-Temporal Timeline",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Current Time: ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))} • Hour ${String.format(Locale.getDefault(), "%.1f", currentHourFloat)}",
                    color = LoomCyan,
                    fontSize = 12.sp
                )
            }

            // Date Switcher Pills
            Surface(color = LoomSurface, shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { currentDate = currentDate.minusDays(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", color = LoomCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (isToday) "TODAY ($dateLabel)" else dateLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isToday) { currentDate = currentDate.plusDays(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("›", color = if (isToday) LoomMuted else LoomCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Zoom Control Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Zoom:", color = LoomMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                LoomZoom.entries.forEach { z ->
                    val isSel = z == zoomMode
                    Button(
                        onClick = { zoomMode = z },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSel) Color(0xFF0284C7) else LoomSurface),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(z.label, fontSize = 11.sp)
                    }
                }
            }
        }

        // SYNTHESIS STATS SUMMARY BAR
        Surface(
            color = LoomSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoomSummaryBadge("KCAL", "${todayMeals.sumOf { it.calories }} kcal", LoomCyan, Icons.Default.LocalFireDepartment)
                LoomSummaryBadge("WATER", signals.waterIntakeMl?.let { "$it ml" } ?: "—", LoomCyan, Icons.Default.WaterDrop)
                LoomSummaryBadge("SCREEN", signals.phoneUsageMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—", LoomPink, Icons.Default.Phonelink)
                LoomSummaryBadge("SLEEP", signals.sleepScore?.let { "$it%" } ?: "—", LoomPurple, Icons.Default.NightsStay)
                LoomSummaryBadge("WORKOUT", signals.activeMinutes?.let { "${it}m" } ?: "—", LoomGreen, Icons.Default.FitnessCenter)
            }
        }

        // TIMELINE CANVAS
        Surface(
            color = LoomSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                val totalWidthDp = hourWidthPx * 24f + 80f

                Column(
                    modifier = Modifier
                        .width(totalWidthDp.dp)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    // HOUR MARKERS HEADER
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0..23).forEach { hour ->
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:00", hour),
                                color = LoomMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(hourWidthPx.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CANVAS GRID & EVENT BLOCKS
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val heightPx = size.height

                            // Hour grid lines
                            (0..24).forEach { hour ->
                                val x = hour * hourWidthPx * density
                                drawLine(
                                    color = LoomGrid,
                                    start = Offset(x, 0f),
                                    end = Offset(x, heightPx),
                                    strokeWidth = 1f
                                )
                            }

                            // Current Live Time Line (if today)
                            if (isToday) {
                                val currentX = currentHourFloat * hourWidthPx * density
                                drawLine(
                                    color = LoomCyan,
                                    start = Offset(currentX, 0f),
                                    end = Offset(currentX, heightPx),
                                    strokeWidth = 3f
                                )
                                drawCircle(
                                    color = LoomCyan,
                                    radius = 6f,
                                    center = Offset(currentX, 0f)
                                )
                            }
                        }

                        // EVENT BLOCKS OVERLAY
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            timelineEvents.forEach { event ->
                                val startOffsetDp = event.startHour * hourWidthPx
                                val blockWidthDp = event.durationHours * hourWidthPx
                                val isSelected = selectedEvent?.id == event.id

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.width(startOffsetDp.dp))
                                    Surface(
                                        color = event.color.copy(alpha = if (isSelected) 0.35f else 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .width(blockWidthDp.coerceAtLeast(50f).dp)
                                            .height(44.dp)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = event.color,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedEvent = event }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = event.icon,
                                                contentDescription = null,
                                                tint = event.color,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = event.title,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = event.subtitle,
                                                    color = LoomMuted,
                                                    fontSize = 9.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // INSPECTOR CARD FOR SELECTED EVENT
        selectedEvent?.let { event ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = LoomSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(event.color.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(event.icon, contentDescription = null, tint = event.color, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "${event.subtitle} • Lane: ${event.lane} • Start Hour: ${String.format(Locale.getDefault(), "%.1f", event.startHour)}h", color = LoomMuted, fontSize = 12.sp)
                        }
                    }

                    IconButton(onClick = { selectedEvent = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = LoomMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoomSummaryBadge(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = label, color = LoomMuted, fontSize = 10.sp)
        }
    }
}
