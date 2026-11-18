package com.health.friday.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.agentic.SystemController
import com.health.friday.ai.LocalLlmClient
import com.health.friday.alarms.AlarmRepository
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayText
import kotlinx.coroutines.launch
import android.content.Intent
import com.health.friday.sync.drive.DriveConnectionActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    alarmRepository: AlarmRepository,
    systemController: SystemController,
    modifier: Modifier = Modifier
) {
    var savedKey by remember { mutableStateOf(settings.getApiKey()) }
    var savedModel by remember { mutableStateOf(settings.getModel()) }
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(settings.getModel()) }
    var localLlmEnabled by remember { mutableStateOf(settings.isLocalLlmEnabled()) }

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "Settings",
                subtitle = "App Configuration & Alarm Telemetry"
            )
        }

        // Google Drive Sync Stage 1 Card
        item {
            val context = LocalContext.current

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
                        text = "Google Drive Sync",
                        color = FridayCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Connect FRIDAY to Google Drive and test file upload/download.",
                        color = FridayMuted,
                        fontSize = 13.sp
                    )

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    DriveConnectionActivity::class.java
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FridayCyan,
                            contentColor = FridayBackground
                        )
                    ) {
                        Text("Configure Drive")
                    }
                }
            }
        }

        // Scheduled Alarms Card
        item {
            val alarms by alarmRepository.getAlarms().collectAsState(initial = emptyList())
            val coroutineScope = rememberCoroutineScope()
            val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FridayCard)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏰ Scheduled Alarms (${alarms.size})",
                            color = FridayCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (alarms.isEmpty()) {
                        Text(
                            text = "No alarms configured. Ask FRIDAY to set an alarm for you anytime!",
                            color = FridayMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            alarms.forEach { alarm ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alarm.title,
                                            color = FridayText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${timeFormatter.format(Date(alarm.timeMillis))} • ${if (alarm.repeatDaily) "Repeats daily" else "One-time"}",
                                            color = FridayMuted,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Switch(
                                            checked = alarm.enabled,
                                            onCheckedChange = { isChecked ->
                                                coroutineScope.launch {
                                                    if (isChecked) {
                                                        alarmRepository.enableAlarm(alarm)
                                                    } else {
                                                        alarmRepository.disableAlarm(alarm)
                                                    }
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = FridayBackground,
                                                checkedTrackColor = FridayCyan,
                                                uncheckedThumbColor = FridayMuted,
                                                uncheckedTrackColor = FridayBackground
                                            )
                                        )

                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    alarmRepository.deleteAlarm(alarm)
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "Delete",
                                                color = FridayOrange,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Engine / Gemini Model Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FridayCard)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AI Engine (Gemini & Local LLM)",
                        color = FridayCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Run AI offline (Local LLM)",
                                color = FridayText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bypasses Gemini. No internet required.",
                                color = FridayMuted,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = localLlmEnabled,
                            onCheckedChange = { isChecked ->
                                settings.saveLocalLlmEnabled(isChecked)
                                localLlmEnabled = isChecked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FridayBackground,
                                checkedTrackColor = FridayCyan,
                                uncheckedThumbColor = FridayMuted,
                                uncheckedTrackColor = FridayBackground
                            )
                        )
                    }

                    if (localLlmEnabled) {
                        val context = LocalContext.current
                        val modelFile = remember(localLlmEnabled) { LocalLlmClient.getModelFile(context) }
                        if (modelFile != null) {
                            Text(
                                text = "Local Model Found: ${modelFile.name} (${modelFile.length() / (1024 * 1024)} MB)",
                                color = FridayGreen,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "Model Missing! Place model file in:\n${context.getExternalFilesDir(null)?.absolutePath ?: "App files dir"}/gemma-4-E2B-it.litertlm",
                                color = FridayOrange,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (savedKey.isEmpty()) {
                            "Cloud mode disabled. No Gemini key set."
                        } else {
                            "Gemini on. Key ending ${savedKey.takeLast(4)}. Model: $savedModel"
                        },
                        color = if (savedKey.isEmpty()) FridayOrange else FridayGreen,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API key") },
                        placeholder = { Text("Paste a new key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    settings.saveApiKey(keyInput)
                                    savedKey = settings.getApiKey()
                                    keyInput = ""
                                }
                                settings.saveModel(modelInput)
                                savedModel = settings.getModel()
                                modelInput = savedModel
                            },
                            enabled = keyInput.isNotBlank() || modelInput.trim() != savedModel,
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
                                Text(text = "Remove key", color = FridayMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
