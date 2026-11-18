package com.health.friday.desktop.features.overlay

import androidx.compose.runtime.Composable
import com.health.friday.desktop.features.assistant.AssistantOverlayWindow as AssistantOverlayWindowImpl

@Composable
fun AssistantOverlayWindow(onClose: () -> Unit) {
    AssistantOverlayWindowImpl(onClose = onClose)
}
