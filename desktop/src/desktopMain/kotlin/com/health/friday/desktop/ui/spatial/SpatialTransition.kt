package com.health.friday.desktop.ui.spatial

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.EaseInQuint

fun spatialZTransition(): ContentTransform {
    val duration = 600

    // New Screen flies in from the front (Scale 1.05 -> 1.0) and fades in
    val enter = fadeIn(
        animationSpec = tween(duration, easing = EaseOutQuint)
    ) + scaleIn(
        initialScale = 1.05f,
        animationSpec = tween(duration, easing = EaseOutQuint)
    )

    // Old screen falls backward into the glass (Scale 1.0 -> 0.95) and fades out
    val exit = fadeOut(
        animationSpec = tween(duration / 2, easing = EaseInQuint)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(duration, easing = EaseInQuint)
    )

    return enter togetherWith exit
}
