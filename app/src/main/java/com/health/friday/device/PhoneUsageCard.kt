
package com.health.friday.device

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object NoAccess : UsageUiState
    data object Error : UsageUiState
    data class Ready(val summary: UsageSummary) : UsageUiState
}

private data class UsageSegment(
    val label: String,
    val millis: Long,
    val percentage: Float
)

private fun formatDuration(millis: Long): String {

    val totalMinutes = millis / 60_000

    if (totalMinutes < 1) {
        return "<1m"
    }

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun buildUsageSegments(
    summary: UsageSummary
): List<UsageSegment> {

    val total = summary.totalMillis

    if (total <= 0L) {
        return emptyList()
    }

    val apps = summary.topApps
        .filter { it.millis > 0L }
        .take(5)

    val usedByTopApps =
        apps.sumOf { it.millis }

    val otherMillis =
        (total - usedByTopApps).coerceAtLeast(0L)

    val segments = mutableListOf<UsageSegment>()

    for (app in apps) {

        val percentage =
            (app.millis.toDouble() / total.toDouble() * 100.0)
                .toFloat()

        segments.add(
            UsageSegment(
                label = app.label,
                millis = app.millis,
                percentage = percentage
            )
        )
    }

    if (otherMillis > 0L) {

        segments.add(
            UsageSegment(
                label = "Other",
                millis = otherMillis,
                percentage =
                    (otherMillis.toDouble() / total.toDouble() * 100.0)
                        .toFloat()
            )
        )
    }

    return segments
}

@Composable
fun PhoneUsageCard(
    repository: DeviceUsageRepository,
    refreshKey: Int,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    var state by remember {
        mutableStateOf<UsageUiState>(UsageUiState.Loading)
    }

    LaunchedEffect(refreshKey) {

        state =
            if (!repository.hasAccess()) {
                UsageUiState.NoAccess
            } else {
                try {
                    UsageUiState.Ready(
                        repository.getTodayUsage()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    UsageUiState.Error
                }
            }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // HEADER

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "PHONE USAGE",
                        color = FridayText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Text(
                        text = "How your screen time is distributed",
                        color = FridayMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = FridayCyan,
                            shape = CircleShape
                        )
                )
            }

            when (val current = state) {

                // LOADING

                is UsageUiState.Loading -> {

                    Text(
                        text = "Reading device usage...",
                        color = FridayMuted,
                        fontSize = 14.sp
                    )
                }

                // NO ACCESS

                is UsageUiState.NoAccess -> {

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = "Usage access is required",
                            color = FridayText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Allow FRIDAY to read screen-time data from Android settings.",
                            color = FridayMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_USAGE_ACCESS_SETTINGS
                                        )
                                    )
                                } catch (e: Exception) {
                                    // Settings page not available on this device.
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FridayCyan,
                                contentColor = FridayBackground
                            )
                        ) {
                            Text(
                                text = "Grant access",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ERROR

                is UsageUiState.Error -> {

                    Text(
                        text = "Couldn't read usage right now.",
                        color = FridayMuted,
                        fontSize = 14.sp
                    )
                }

                // READY

                is UsageUiState.Ready -> {

                    val segments =
                        remember(current.summary) {
                            buildUsageSegments(
                                current.summary
                            )
                        }

                    // RING

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 4.dp,
                                bottom = 4.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        UsageRing(
                            segments = segments,
                            totalMillis = current.summary.totalMillis
                        )
                    }

                    // BREAKDOWN

                    if (segments.isNotEmpty()) {

                        Column(
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {

                            Text(
                                text = "USAGE BREAKDOWN",
                                color = FridayMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            segments.forEachIndexed { index, segment ->

                                UsageBreakdownRow(
                                    segment = segment,
                                    index = index
                                )
                            }
                        }
                    } else {

                        Text(
                            text = "No screen time recorded yet.",
                            color = FridayMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRing(
    segments: List<UsageSegment>,
    totalMillis: Long
) {

    val ringColors = listOf(
        FridayCyan,
        FridayBlue,
        FridayCyan.copy(alpha = 0.72f),
        FridayBlue.copy(alpha = 0.72f),
        FridayCyan.copy(alpha = 0.48f),
        FridayMuted.copy(alpha = 0.35f)
    )

    Box(
        modifier = Modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier.size(190.dp)
        ) {

            val strokeWidth = 18.dp.toPx()

            val diameter =
                size.minDimension - strokeWidth

            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )

            // Base ring

            drawArc(
                color = FridayMuted.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    diameter,
                    diameter
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            var currentAngle = -90f

            segments.forEachIndexed { index, segment ->

                val sweep =
                    segment.percentage / 100f * 360f

                if (sweep > 0f) {

                    drawArc(
                        color = ringColors[
                            index.coerceAtMost(
                                ringColors.lastIndex
                            )
                        ],
                        startAngle = currentAngle,
                        sweepAngle = sweep - 2f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = androidx.compose.ui.geometry.Size(
                            diameter,
                            diameter
                        ),
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )

                    currentAngle += sweep
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = formatDuration(totalMillis),
                color = FridayText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "SCREEN TIME",
                color = FridayMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 3.dp)
            )

            Text(
                text = "TODAY",
                color = FridayCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun UsageBreakdownRow(
    segment: UsageSegment,
    index: Int
) {

    val ringColors = listOf(
        FridayCyan,
        FridayBlue,
        FridayCyan.copy(alpha = 0.72f),
        FridayBlue.copy(alpha = 0.72f),
        FridayCyan.copy(alpha = 0.48f),
        FridayMuted.copy(alpha = 0.35f)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    color = ringColors[
                        index.coerceAtMost(
                            ringColors.lastIndex
                        )
                    ],
                    shape = CircleShape
                )
        )

        Spacer(
            modifier = Modifier.size(10.dp)
        )

        Text(
            text = segment.label,
            color = FridayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${segment.percentage.roundToInt()}%",
            color = FridayMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Text(
            text = formatDuration(segment.millis),
            color = FridayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

