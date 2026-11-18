package com.health.friday.desktop.features.widgets

import androidx.compose.foundation.layout.*
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
import com.health.friday.desktop.ui.components.WidgetContainer
import com.health.friday.shared.models.DailySignalDto

@Composable
fun HealthVitalsWidget(
    signals: DailySignalDto,
    modifier: Modifier = Modifier
) {
    WidgetContainer(title = "Health & Vitals", modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Health",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = signals.sleepScore?.let { "Sleep Score: $it%" } ?: "Sleep: Synced live",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Text(
                text = signals.waterIntakeMl?.let { "Hydration: $it ml" } ?: "Water: Goal 2,500 ml",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
