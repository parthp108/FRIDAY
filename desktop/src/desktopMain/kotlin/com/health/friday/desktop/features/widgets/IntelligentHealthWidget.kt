package com.health.friday.desktop.features.widgets

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.health.friday.desktop.features.intelligence.WidgetIntelligenceState
import com.health.friday.desktop.features.intelligence.WidgetMode
import com.health.friday.desktop.ui.components.WidgetContainer
import com.health.friday.desktop.ui.components.reactiveGlow
import com.health.friday.shared.models.DailySignalDto

@Composable
fun IntelligentHealthWidget(
    signals: DailySignalDto,
    intelligence: WidgetIntelligenceState,
    config: WidgetConfig? = null,
    modifier: Modifier = Modifier
) {
    WidgetContainer(title = "Health Intelligence", config = config, modifier = modifier) {

        // AnimatedContent morphs the UI layout perfectly between states
        AnimatedContent(
            targetState = intelligence.mode,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "health_widget_morph"
        ) { targetMode ->

            when (targetMode) {
                WidgetMode.MINIMAL -> {
                    // Just a tiny, pulsing glowing ring indicating normal status
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Normal",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(32.dp).reactiveGlow(isActive = true, shape = CircleShape)
                        )
                    }
                }
                WidgetMode.EXPANDED -> {
                    // Standard text view
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            text = signals.sleepScore?.let { "Sleep Score: $it%" } ?: "Sleep: Synced live",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = signals.waterIntakeMl?.let { "Hydration: $it ml" } ?: "Water: Goal 2,500 ml",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                WidgetMode.IMMERSIVE -> {
                    // Critical Alert view - Deep Red colors, large typography
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "⚠️ ${intelligence.overrideReason ?: "Anomaly Detected"}",
                            color = Color(0xFFFF3B30),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = signals.activeMinutes?.let { "$it mins Active" } ?: "Active Vitals",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = signals.sleepScore?.let { "Sleep: $it%" } ?: "Synced",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFFF9500)
                            )
                        }
                    }
                }
                WidgetMode.HIDDEN -> { /* Renders nothing */ }
            }
        }
    }
}
