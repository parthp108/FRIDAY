
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText

@Composable
fun AlarmScreen(
    alarmTitle: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {

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
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier
                .height(18.dp)
        )

        Text(
            text = alarmTitle,
            color = FridayText,
            fontSize = 32.sp
        )

        Spacer(
            modifier = Modifier
                .height(12.dp)
        )

        Text(
            text = "FRIDAY alarm",
            color = FridayMuted,
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier
                .height(48.dp)
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
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
                fontSize = 17.sp
            )
        }

        Spacer(
            modifier = Modifier
                .height(14.dp)
        )

        Button(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        FridayMuted,
                    contentColor =
                        FridayBackground
                )
        ) {

            Text(
                text = "Snooze 10 minutes",
                fontSize = 16.sp
            )
        }
    }
}

