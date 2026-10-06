
package com.health.friday.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.health.friday.alarms.AlarmRepository
import com.health.friday.ui.components.InfoCard
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    alarmRepository: AlarmRepository,
    modifier: Modifier = Modifier
) {

    var savedKey by remember {
        mutableStateOf(settings.getApiKey())
    }

    var savedModel by remember {
        mutableStateOf(settings.getModel())
    }

    var keyInput by remember {
        mutableStateOf("")
    }

    var modelInput by remember {
        mutableStateOf(settings.getModel())
    }

    val alarms by
    alarmRepository
        .getAlarms()
        .collectAsState(initial = emptyList())

    val timeFormat =
        remember {
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            )
        }

    val dateTimeFormat =
        remember {
            SimpleDateFormat(
                "dd MMM yyyy, h:mm a",
                Locale.getDefault()
            )
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            ScreenHeader(
                title = "Settings",
                subtitle = "FRIDAY configuration"
            )
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = FridayCard
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        text = "AI engine (Gemini)",
                        color = FridayCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            if (savedKey.isEmpty()) {
                                "Offline mode. No Gemini key set."
                            } else {
                                "Gemini on. Key ending ${savedKey.takeLast(4)}. " +
                                        "Model: $savedModel"
                            },
                        color =
                            if (savedKey.isEmpty()) {
                                FridayOrange
                            } else {
                                FridayGreen
                            },
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Gemini API key")
                        },
                        placeholder = {
                            Text("Paste a new key")
                        },
                        singleLine = true,
                        visualTransformation =
                            PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = {
                            modelInput = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Model")
                        },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Button(
                            onClick = {

                                if (keyInput.isNotBlank()) {
                                    settings.saveApiKey(keyInput)
                                    savedKey =
                                        settings.getApiKey()
                                    keyInput = ""
                                }

                                settings.saveModel(modelInput)
                                savedModel =
                                    settings.getModel()
                                modelInput = savedModel
                            },
                            enabled =
                                keyInput.isNotBlank() ||
                                        modelInput.trim() !=
                                        savedModel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FridayCyan,
                                contentColor = FridayBackground
                            )
                        ) {
                            Text("Save")
                        }

                        if (savedKey.isNotEmpty()) {

                            TextButton(
                                onClick = {
                                    settings.saveApiKey("")
                                    savedKey = ""
                                }
                            ) {
                                Text(
                                    text = "Remove key",
                                    color = FridayMuted
                                )
                            }
                        }
                    }

                    Text(
                        text =
                            "Get a free key at aistudio.google.com/apikey. " +
                                    "The key stays on this phone.",
                        color = FridayMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = FridayCard
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        text = "Alarms",
                        color = FridayCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "Your FRIDAY alarms use the phone's local time.",
                        color = FridayMuted,
                        fontSize = 13.sp
                    )

                    val enabledAlarms =
                        alarms.filter {
                            it.enabled
                        }

                    if (enabledAlarms.isEmpty()) {

                        Text(
                            text = "No active alarms.",
                            color = FridayMuted,
                            fontSize = 14.sp
                        )

                    } else {

                        enabledAlarms.forEach { alarm ->

                            val time =
                                timeFormat.format(
                                    Date(alarm.timeMillis)
                                )

                            val detail =
                                if (alarm.repeatDaily) {
                                    "Every day at $time"
                                } else {
                                    "Once • " +
                                            dateTimeFormat.format(
                                                Date(
                                                    alarm.timeMillis
                                                )
                                            )
                                }

                            Card(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                shape =
                                    RoundedCornerShape(14.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            FridayBackground
                                    )
                            ) {

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Column(
                                        modifier =
                                            Modifier.weight(1f),
                                        verticalArrangement =
                                            Arrangement.spacedBy(
                                                4.dp
                                            )
                                    ) {

                                        Text(
                                            text = alarm.title,
                                            color = FridayText,
                                            fontSize = 15.sp,
                                            fontWeight =
                                                FontWeight.SemiBold
                                        )

                                        Text(
                                            text = detail,
                                            color = FridayCyan,
                                            fontSize = 14.sp
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            kotlinx.coroutines.MainScope()
                                                .launch {
                                                    alarmRepository
                                                        .deleteAlarm(alarm)
                                                }
                                        }
                                    ) {
                                        Text(
                                            text = "Delete",
                                            color = FridayMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            InfoCard(
                title = "What Gemini sees",
                body =
                    "Your chat messages and the results of tools like your food " +
                            "and water log. Journal text is never sent from here. " +
                            "On Google's free tier, content may be used to improve " +
                            "their products.",
                accent = FridayBlue
            )
        }

        item {
            InfoCard(
                title = "Goals",
                body =
                    "Calorie and water goals are fixed at 2500 for now. " +
                            "Editable goals come later.",
                accent = FridayBlue
            )
        }
    }
}

