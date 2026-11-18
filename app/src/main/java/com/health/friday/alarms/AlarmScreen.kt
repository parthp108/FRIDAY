
package com.health.friday.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AlarmScreen(
    alarmTitle: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    var currentTimeString by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                FridayBackground
            )
            .padding(28.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "ALARM",
            color = FridayCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(
            modifier = Modifier
                .height(24.dp)
        )

        Text(
            text = currentTimeString,
            color = FridayText,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier
                .height(12.dp)
        )

        Text(
            text = alarmTitle,
            color = FridayCyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(
            modifier = Modifier
                .height(64.dp)
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        FridayCyan,
                    contentColor =
                        FridayBackground
                )
        ) {

            Text(
                text = "Dismiss",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier
                .height(16.dp)
        )

        Button(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        FridayMuted.copy(alpha = 0.3f),
                    contentColor =
                        FridayText
                )
        ) {

            Text(
                text = "Snooze 10 minutes",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


