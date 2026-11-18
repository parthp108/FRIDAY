package com.health.friday.loom

import android.graphics.Typeface
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.FridayApplication
import com.health.friday.data.local.JournalEntry
import com.health.friday.data.local.Meal
import com.health.friday.data.local.TodoItem
import com.health.friday.data.local.WaterEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

private val LoomBackground = Color(0xFF090D12)
private val LoomSurface = Color(0xFF111821)
private val LoomSurfaceRaised = Color(0xFF17212B)
private val LoomCyan = Color(0xFF55E6F2)
private val LoomText = Color(0xFFF0F5F8)
private val LoomMuted = Color(0xFF8796A5)
private val LoomGrid = Color(0xFF273440)
private val LoomPurple = Color(0xFFB39DFF)
private val LoomGreen = Color(0xFF75D6A0)
private val LoomAmber = Color(0xFFFFC477)
private val LoomPink = Color(0xFFFF85A1)

private fun currentHour(): Float {
    val now = LocalTime.now()
    return now.hour + now.minute / 60f
}

enum class ZoomMode(val hourWidthDp: Float, val label: String) {
    FULL_DAY(44f, "24h"),
    STANDARD(88f, "12h"),
    FOCUS(180f, "4h")
}

@Composable
fun LoomRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as FridayApplication).container
    }

    var date by remember { mutableStateOf(LocalDate.now()) }
    var day by remember { mutableStateOf<LoomDay?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(date, refreshTrigger) {
        while (true) {
            try {
                day = container.loomDayBuilder.build(date)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep showing the last good data.
            }

            if (date != LocalDate.now()) break
            delay(120_000)
        }
    }

    LoomScreen(
        day = day?.takeIf { it.date == date },
        date = date,
        onPreviousDay = { date = date.minusDays(1) },
        onNextDay = { if (date.isBefore(LocalDate.now())) date = date.plusDays(1) },
        onDataChanged = { refreshTrigger++ },
        modifier = modifier
    )
}

@Composable
fun LoomScreen(
    day: LoomDay?,
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDataChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember(context) {
        (context.applicationContext as FridayApplication).container
    }

    val density = LocalDensity.current
    var zoomMode by remember { mutableStateOf(ZoomMode.STANDARD) }
    val hourWidth = with(density) { zoomMode.hourWidthDp.dp.toPx() }
    val isToday = date == LocalDate.now()

    var nowHour by remember { mutableFloatStateOf(currentHour()) }
    var selectedEvent by remember { mutableStateOf<LoomEvent?>(null) }
    var showQuickLogSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowHour = currentHour()
        }
    }

    var dragOffset by remember(date, hourWidth) {
        mutableFloatStateOf(if (isToday) -(currentHour() - 12f) * hourWidth else 0f)
    }
    val maxDrag = 12f * hourWidth
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))

    val labelPaint = remember(density) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            color = android.graphics.Color.rgb(135, 150, 165)
        }
    }

    val lanePaint = remember(density) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.08f
        }
    }

    val eventPaint = remember(density) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = android.graphics.Color.rgb(240, 245, 248)
        }
    }

    val mutedPaint = remember(density) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = android.graphics.Color.rgb(135, 150, 165)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LoomBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FRIDAY  /  PERSONAL OS",
                    color = LoomMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The Loom",
                    color = LoomText,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
            }

            // + Quick Log Button
            Button(
                onClick = { showQuickLogSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = LoomCyan, contentColor = LoomBackground),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Quick Log", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Quick Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))

        // Date switcher
        Surface(
            color = LoomSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onPreviousDay() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = LoomCyan, fontSize = 26.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isToday) "TODAY" else "PAST DAY",
                        color = LoomMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = dateLabel,
                        color = LoomText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isToday) { onNextDay() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "›",
                        color = if (isToday) LoomGrid else LoomCyan,
                        fontSize = 26.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Zoom Scale Controls & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIMELINE CANVAS",
                color = LoomMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            // Zoom Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZoomMode.entries.forEach { mode ->
                    val isSelected = zoomMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) LoomCyan else LoomSurface)
                            .clickable { zoomMode = mode }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) LoomBackground else LoomMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 5-Lane Timeline Canvas
        Surface(
            color = LoomSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hourWidth) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = (dragOffset + dragAmount).coerceIn(-maxDrag, maxDrag)
                            }
                        )
                    }
                    .pointerInput(day) {
                        detectTapGestures { offset ->
                            val canvasWidth = size.width
                            val centerX = canvasWidth / 2f
                            val tappedHour = 12f + (offset.x - centerX - dragOffset) / hourWidth
                            val tappedLaneIndex = ((offset.y - 62.dp.toPx()) / 72.dp.toPx()).toInt()

                            val loaded = day ?: return@detectTapGestures
                            val candidates = when (tappedLaneIndex) {
                                0 -> loaded.sleep
                                1 -> loaded.meals + loaded.water
                                2 -> loaded.workouts
                                3 -> loaded.tasks
                                4 -> loaded.journals
                                else -> emptyList()
                            }

                            selectedEvent = candidates.minByOrNull { abs(it.startHour - tappedHour) }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f

                fun xFor(hour: Float): Float = centerX + (hour - 12f) * hourWidth + dragOffset

                val labelInset = 17.dp.toPx()
                val timeRowY = 31.dp.toPx()
                val laneStartY = 62.dp.toPx()
                val laneGap = 72.dp.toPx()
                val laneHeight = 58.dp.toPx()
                val laneEndX = canvasWidth - labelInset

                // Hour grid
                for (hour in -48..72) {
                    val x = xFor(hour.toFloat())
                    if (x < 0f || x > canvasWidth) continue

                    val hourOfDay = ((hour % 24) + 24) % 24
                    val isMajor = hourOfDay % 6 == 0
                    val isNoon = hourOfDay == 12

                    drawLine(
                        color = if (isMajor) LoomGrid.copy(alpha = 0.95f) else LoomGrid.copy(alpha = 0.38f),
                        start = Offset(x, timeRowY + 12.dp.toPx()),
                        end = Offset(x, canvasHeight - 20.dp.toPx()),
                        strokeWidth = if (isMajor) 1.dp.toPx() else 0.6.dp.toPx()
                    )

                    if (isMajor) {
                        val label = when (hourOfDay) {
                            0 -> "12 AM"
                            6 -> "6 AM"
                            12 -> "12 PM"
                            18 -> "6 PM"
                            else -> ""
                        }

                        if (label.isNotEmpty()) {
                            labelPaint.color =
                                if (isNoon) android.graphics.Color.rgb(85, 230, 242)
                                else android.graphics.Color.rgb(135, 150, 165)

                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                x + 5.dp.toPx(),
                                timeRowY,
                                labelPaint
                            )
                        }
                    }
                }

                // 5 Lanes Backgrounds and Colors
                val lanes = listOf(
                    Pair("REST", LoomPurple),
                    Pair("NUTRITION", LoomCyan),
                    Pair("ACTIVITY", LoomGreen),
                    Pair("TASKS", LoomAmber),
                    Pair("MIND", LoomPink)
                )

                lanes.forEachIndexed { index, lane ->
                    val y = laneStartY + index * laneGap
                    val color = lane.second

                    drawRoundRect(
                        color = LoomSurfaceRaised.copy(alpha = 0.52f),
                        topLeft = Offset(labelInset, y),
                        size = Size(laneEndX - labelInset, laneHeight),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )

                    drawRoundRect(
                        color = color.copy(alpha = 0.9f),
                        topLeft = Offset(labelInset, y + 10.dp.toPx()),
                        size = Size(3.dp.toPx(), laneHeight - 20.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }

                // Render Events
                val loaded = day
                fun laneTop(index: Int) = laneStartY + index * laneGap

                if (loaded != null) {
                    // 1. REST
                    val restY = laneTop(0)
                    loaded.sleep.forEach { event ->
                        val x1 = xFor(event.startHour)
                        val x2 = xFor(event.endHour ?: event.startHour)

                        drawRoundRect(
                            color = LoomPurple.copy(alpha = 0.55f),
                            topLeft = Offset(x1, restY + 22.dp.toPx()),
                            size = Size((x2 - x1).coerceAtLeast(2.dp.toPx()), 24.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )

                        if (x2 - x1 > 60.dp.toPx()) {
                            eventPaint.textAlign = AndroidPaint.Align.CENTER
                            drawContext.canvas.nativeCanvas.drawText(
                                event.title,
                                (x1 + x2) / 2f,
                                restY + 38.dp.toPx(),
                                eventPaint
                            )
                            eventPaint.textAlign = AndroidPaint.Align.LEFT
                        }
                    }

                    // 2. NUTRITION
                    val foodY = laneTop(1)
                    loaded.meals.forEach { event ->
                        val x = xFor(event.startHour)
                        val isSel = selectedEvent?.id == event.id

                        drawCircle(
                            color = if (isSel) Color.White else LoomCyan,
                            radius = if (isSel) 8.dp.toPx() else 6.dp.toPx(),
                            center = Offset(x, foodY + 32.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            event.subtitle,
                            x + 10.dp.toPx(),
                            foodY + 36.dp.toPx(),
                            eventPaint
                        )
                    }

                    loaded.water.forEach { event ->
                        val x = xFor(event.startHour)
                        drawCircle(
                            color = LoomCyan.copy(alpha = 0.6f),
                            radius = 3.dp.toPx(),
                            center = Offset(x, foodY + 48.dp.toPx())
                        )
                    }

                    // 3. ACTIVITY (Workouts & Daily Metrics)
                    val actY = laneTop(2)
                    loaded.workouts.forEach { event ->
                        val x1 = xFor(event.startHour)
                        val x2 = xFor(event.endHour ?: (event.startHour + 0.75f))

                        drawRoundRect(
                            color = LoomGreen.copy(alpha = 0.65f),
                            topLeft = Offset(x1, actY + 22.dp.toPx()),
                            size = Size((x2 - x1).coerceAtLeast(16.dp.toPx()), 24.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                    }

                    // 4. TASKS
                    val taskY = laneTop(3)
                    loaded.tasks.forEach { event ->
                        val x = xFor(event.startHour)
                        val center = Offset(x, taskY + 32.dp.toPx())
                        val isSel = selectedEvent?.id == event.id

                        if (event.done) {
                            drawCircle(color = if (isSel) Color.White else LoomAmber, radius = 5.dp.toPx(), center = center)
                        } else {
                            drawCircle(
                                color = if (isSel) Color.White else LoomAmber,
                                radius = 5.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            event.title.take(16),
                            x + 10.dp.toPx(),
                            taskY + 36.dp.toPx(),
                            eventPaint
                        )
                    }

                    // 5. MIND (Journal Nodes)
                    val mindY = laneTop(4)
                    loaded.journals.forEach { event ->
                        val x = xFor(event.startHour)
                        val isSel = selectedEvent?.id == event.id

                        drawCircle(
                            color = if (isSel) Color.White else LoomPink,
                            radius = if (isSel) 7.dp.toPx() else 5.dp.toPx(),
                            center = Offset(x, mindY + 32.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            event.subtitle.take(18),
                            x + 10.dp.toPx(),
                            mindY + 36.dp.toPx(),
                            eventPaint
                        )
                    }

                    // Empty lane notes
                    val noteX = labelInset + 13.dp.toPx()
                    if (loaded.sleep.isEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            if (loaded.sleepMinutes != null) "Slept ${loaded.sleepMinutes / 60}h ${loaded.sleepMinutes % 60}m"
                            else "No sleep data",
                            noteX, restY + 38.dp.toPx(), mutedPaint
                        )
                    }

                    if (loaded.meals.isEmpty() && loaded.water.isEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText("Nothing logged", noteX, foodY + 38.dp.toPx(), mutedPaint)
                    }

                    if (loaded.workouts.isEmpty()) {
                        val actParts = mutableListOf<String>()
                        loaded.steps?.let { actParts.add(String.format(Locale.US, "%,d steps", it)) }
                        loaded.screenMinutes?.let { actParts.add("screen ${it / 60}h ${it % 60}m") }
                        drawContext.canvas.nativeCanvas.drawText(
                            if (actParts.isEmpty()) "No activity data" else actParts.joinToString("  ·  "),
                            noteX, actY + 38.dp.toPx(), mutedPaint
                        )
                    }

                    if (loaded.tasks.isEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText("No timed tasks", noteX, taskY + 38.dp.toPx(), mutedPaint)
                    }

                    if (loaded.journals.isEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText("No journal reflections", noteX, mindY + 38.dp.toPx(), mutedPaint)
                    }
                }

                // Draw Lane Titles
                lanes.forEachIndexed { index, lane ->
                    val color = lane.second
                    lanePaint.color = android.graphics.Color.rgb(
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        lane.first,
                        labelInset + 13.dp.toPx(),
                        laneTop(index) + 16.dp.toPx(),
                        lanePaint
                    )
                }

                // Current time marker (today only)
                if (isToday) {
                    val nowX = xFor(nowHour)
                    if (nowX in 0f..canvasWidth) {
                        drawLine(
                            color = LoomCyan.copy(alpha = 0.12f),
                            start = Offset(nowX, timeRowY + 8.dp.toPx()),
                            end = Offset(nowX, canvasHeight - 18.dp.toPx()),
                            strokeWidth = 8.dp.toPx()
                        )
                        drawLine(
                            color = LoomCyan,
                            start = Offset(nowX, timeRowY + 8.dp.toPx()),
                            end = Offset(nowX, canvasHeight - 18.dp.toPx()),
                            strokeWidth = 1.3.dp.toPx()
                        )
                        drawCircle(
                            color = LoomCyan,
                            radius = 4.dp.toPx(),
                            center = Offset(nowX, timeRowY + 8.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoomLegendDot(LoomPurple, "Rest")
            LoomLegendDot(LoomCyan, "Nutrition")
            LoomLegendDot(LoomGreen, "Activity")
            LoomLegendDot(LoomAmber, "Tasks")
            LoomLegendDot(LoomPink, "Mind")
        }

        Spacer(Modifier.height(18.dp))

        // Selected Event Inspection Sheet
        AnimatedVisibility(
            visible = selectedEvent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedEvent?.let { event ->
                LoomEventSheet(event = event, onDismiss = { selectedEvent = null })
            }
        }

        // Quick Log Sheet
        AnimatedVisibility(
            visible = showQuickLogSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoomQuickLogSheet(
                onDismiss = { showQuickLogSheet = false },
                onLogged = {
                    showQuickLogSheet = false
                    onDataChanged()
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        // AI Loom Synthesis & Summary Card
        Surface(
            color = LoomSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "LOOM SYNTHESIS",
                    color = LoomCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = day?.synthesisText?.ifEmpty { "Loading day synthesis..." } ?: "No recorded events for this day.",
                    color = LoomText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                if (day != null && day.hasAnything) {
                    Spacer(Modifier.height(14.dp))

                    val lines = mutableListOf<String>()
                    day.kcal?.let { lines.add("• Nutrition: $it kcal") }
                    day.waterMl?.let { lines.add("• Water: $it ml") }
                    day.sleepMinutes?.let { lines.add("• Sleep: ${it / 60}h ${it % 60}m") }
                    day.steps?.let { lines.add("• Steps: ${String.format(Locale.US, "%,d", it)}") }
                    day.screenMinutes?.let { lines.add("• Screen time: ${it / 60}h ${it % 60}m") }
                    lines.add("• Tasks: ${day.tasksCompleted} done, ${day.tasksPending} pending")

                    Text(
                        text = lines.joinToString("\n"),
                        color = LoomMuted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LoomEventSheet(
    event: LoomEvent,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoomSurfaceRaised)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when (event.type) {
                    LoomEventType.REST -> LoomPurple
                    LoomEventType.NUTRITION -> LoomCyan
                    LoomEventType.WATER -> LoomCyan
                    LoomEventType.ACTIVITY, LoomEventType.WORKOUT -> LoomGreen
                    LoomEventType.TASK -> LoomAmber
                    LoomEventType.MIND_JOURNAL -> LoomPink
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = event.type.name,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = LoomMuted)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = event.title,
                color = LoomText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (event.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.subtitle,
                    color = LoomCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Macros if available
            if (event.calories != null || event.proteinGrams != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    event.calories?.let { MacroPill("${it} kcal", LoomCyan) }
                    event.proteinGrams?.let { MacroPill("${it.toInt()}g protein", LoomPurple) }
                    event.carbsGrams?.let { MacroPill("${it.toInt()}g carbs", LoomAmber) }
                    event.fatGrams?.let { MacroPill("${it.toInt()}g fat", LoomGreen) }
                }
            }

            if (event.detailText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = event.detailText,
                    color = LoomMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun MacroPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LoomQuickLogSheet(
    onDismiss: () -> Unit,
    onLogged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember(context) {
        (context.applicationContext as FridayApplication).container
    }

    var selectedType by remember { mutableStateOf(LoomEventType.NUTRITION) }
    var textInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoomSurfaceRaised)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QUICK LOG TO TIMELINE", color = LoomCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = LoomMuted)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Pair(LoomEventType.NUTRITION, "Meal"),
                    Pair(LoomEventType.WATER, "Water"),
                    Pair(LoomEventType.TASK, "Task"),
                    Pair(LoomEventType.MIND_JOURNAL, "Journal")
                ).forEach { (type, label) ->
                    val isSel = selectedType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) LoomCyan else LoomSurface)
                            .clickable { selectedType = type }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) LoomBackground else LoomText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (selectedType) {
                LoomEventType.NUTRITION -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Food Description (e.g., 2 eggs & toast)") },
                        singleLine = true
                    )
                }
                LoomEventType.WATER -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(250, 500, 750).forEach { amount ->
                            Button(
                                onClick = {
                                    scope.launch {
                                        container.waterRepository.addWater(amount)
                                        onLogged()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LoomCyan, contentColor = LoomBackground)
                            ) {
                                Text("+$amount ml")
                            }
                        }
                    }
                }
                LoomEventType.TASK -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task Title") },
                        singleLine = true
                    )
                }
                LoomEventType.MIND_JOURNAL -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reflection Note") }
                    )
                }
                else -> {}
            }

            if (selectedType != LoomEventType.WATER) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (textInput.isBlank()) return@Button
                        scope.launch {
                            when (selectedType) {
                                LoomEventType.NUTRITION -> {
                                    container.nutritionRepository.logEstimated(
                                        name = textInput,
                                        mealType = "snack",
                                        calories = valueInput.toIntOrNull() ?: 350,
                                        protein = 15.0,
                                        carbohydrates = 40.0,
                                        fat = 10.0,
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                                LoomEventType.TASK -> {
                                    container.todoRepository.addTodo(textInput)
                                }
                                LoomEventType.MIND_JOURNAL -> {
                                    container.journalRepository.saveEntry(textInput)
                                }
                                else -> {}
                            }
                            onLogged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LoomCyan, contentColor = LoomBackground)
                ) {
                    Text("Save to Loom", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LoomLegendDot(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = label,
            color = LoomMuted,
            fontSize = 11.sp
        )
    }
}
