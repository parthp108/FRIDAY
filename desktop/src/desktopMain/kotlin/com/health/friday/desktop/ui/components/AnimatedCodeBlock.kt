package com.health.friday.desktop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.theme.FridayTypography
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun AnimatedCodeBlock(code: String, language: String) {
    // 1. Trigger the scan animation when the code block mounts
    var scanProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(code.length) { // Retrigger if code length changes significantly (streaming)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        ) { value, _ -> scanProgress = value }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF131B2B), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = language.uppercase(), color = Color.Gray, fontSize = 10.sp)

            IconButton(
                onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(code), null)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Text("Copy", color = Color(0xFF00E5FF), fontSize = 10.sp)
            }
        }

        Text(
            text = code,
            style = FridayTypography.CodeTechnical,
            color = Color(0xFFE2E8F0),
            modifier = Modifier
                .padding(12.dp)
                .graphicsLayer { alpha = 0.99f } // Required for compositing the BlendMode
                .drawWithContent {
                    drawContent()
                    // 2. Skia Alpha Masking: Creates a sweep reveal effect
                    val width = size.width
                    val revealX = width * scanProgress

                    val sweepBrush = Brush.horizontalGradient(
                        0.0f to Color.White,
                        revealX / width to Color.White,
                        ((revealX + 50f) / width).coerceAtMost(1.0f) to Color.Transparent,
                        1.0f to Color.Transparent,
                        startX = 0f,
                        endX = width
                    )

                    drawRect(
                        brush = sweepBrush,
                        blendMode = BlendMode.DstIn // Masks the text to only show where the brush is opaque
                    )
                }
        )
    }
}
