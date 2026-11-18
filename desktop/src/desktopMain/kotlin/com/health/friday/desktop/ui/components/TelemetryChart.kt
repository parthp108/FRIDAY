package com.health.friday.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun TelemetryChart(dataPoints: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        if (dataPoints.isEmpty()) return@Canvas

        // Draw background grid lines
        val stepY = size.height / 4
        for (i in 0..4) {
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, stepY * i),
                end = androidx.compose.ui.geometry.Offset(size.width, stepY * i)
            )
        }

        // Draw real-time data path
        val maxData = dataPoints.maxOrNull()?.coerceAtLeast(10f) ?: 10f
        val widthPerPoint = size.width / (dataPoints.size.coerceAtLeast(2) - 1)

        val path = Path().apply {
            dataPoints.forEachIndexed { index, value ->
                val x = index * widthPerPoint
                val y = size.height - ((value / maxData) * size.height)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
