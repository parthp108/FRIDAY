package com.health.friday.desktop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.health.friday.desktop.theme.FridayTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatedStreamingText(
    fullText: String,
    isStreaming: Boolean,
    style: TextStyle = FridayTypography.ConversationalAI,
    color: Color = Color.White
) {
    // Split the incoming stream into tokens/words
    val words = remember(fullText) { fullText.split(Regex("(?<=\\s)|(?=\\s)")) }

    FlowRow {
        words.forEachIndexed { index, word ->
            if (word.isNotEmpty()) {
                AnimatedWord(word = word, index = index, style = style, color = color)
            }
        }

        if (isStreaming) {
            // A pulsing, intelligent cursor instead of a blinking block
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse)
            )
            Text(" ✧", style = style, color = Color(0xFF00E5FF), modifier = Modifier.graphicsLayer { this.alpha = alpha })
        }
    }
}

@Composable
private fun AnimatedWord(word: String, index: Int, style: TextStyle, color: Color) {
    // Each word animates independently the moment it enters the Compose tree
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )
    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 10f, // Drops in from slightly below
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
    )

    Text(
        text = word,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translateY
        }
    )
}
