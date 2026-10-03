package com.health.friday.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = FridayText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            color = FridayMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = FridayMuted,
                fontSize = 12.sp
            )

            Text(
                text = value,
                color = accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = detail,
                color = FridayMuted,
                fontSize = 12.sp
            )

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = FridayCardLight
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = body,
                color = FridayMuted,
                fontSize = 14.sp
            )
        }
    }
}