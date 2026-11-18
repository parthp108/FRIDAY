package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.sync.drive.DesktopDriveTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

@Composable
fun DesktopSettingsScreen(
    apiKey: String,
    personaStyle: String,
    selectedLocalModel: String = "llama3.2:1b",
    onUpdateSettings: (String, String, String) -> Unit
) {
    var keyInput by remember { mutableStateOf(apiKey) }
    var selectedPersona by remember { mutableStateOf(personaStyle) }
    var localModelName by remember { mutableStateOf(selectedLocalModel) }
    var localLlmEnabled by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    var driveTestRunning by remember { mutableStateOf(false) }
    var driveTestStatus by remember {
        mutableStateOf("Not connected")
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // CARD 0: GOOGLE DRIVE CONNECTION
        item {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Google Drive Connection",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        "Connect this Windows installation to the same FRIDAY Drive folder used by Android.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Button(
                        enabled = !driveTestRunning,
                        onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Select Google Desktop OAuth credentials"
                                fileSelectionMode = JFileChooser.FILES_ONLY
                            }

                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val credentialsFile = chooser.selectedFile

                                driveTestRunning = true
                                driveTestStatus = "Opening Google authorization..."

                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            DesktopDriveTest.run(credentialsFile)
                                        }
                                    }

                                    driveTestStatus = result.fold(
                                        onSuccess = { "DRIVE TEST PASSED\n\n$it" },
                                        onFailure = {
                                            val detail = it.localizedMessage ?: it.toString()
                                            "DRIVE TEST FAILED\n\n$detail"
                                        }
                                    )

                                    driveTestRunning = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        )
                    ) {
                        Text(
                            if (driveTestRunning) "Testing..." else "Connect & Run Drive Test"
                        )
                    }

                    Text(
                        driveTestStatus,
                        color = if (driveTestStatus.startsWith("DRIVE TEST PASSED"))
                            Color(0xFF4ADE80)
                        else
                            Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            }
        }
        // CARD 1: AI ENGINE & LLM CONFIGURATION
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI Engine Configuration (Local PC Ollama & Cloud Gemini)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Run Local PC AI Model (Ollama at http://localhost:11434)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Bypasses cloud Gemini. Runs 100% offline on PC GPU/CPU.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = localLlmEnabled,
                            onCheckedChange = { localLlmEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                        )
                    }

                    if (localLlmEnabled) {
                        Text("Active Local PC AI Model:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("llama3.2:1b", "gemma:2b", "qwen2.5:1.5b").forEach { m ->
                                val isSel = m == localModelName
                                Button(
                                    onClick = { localModelName = m },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSel) Color(0xFF0284C7) else Color(0xFF334155))
                                ) {
                                    Text(m, fontSize = 11.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = localModelName,
                            onValueChange = { localModelName = it },
                            label = { Text("Local Model Identifier") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A))
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    Text("Gemini API Key (Cloud Fallback):", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste your Gemini API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Assistant Persona Style:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("F.R.I.D.A.Y. (Iron Man)", "Casual Friend", "Strict Productivity Coach").forEach { p ->
                            val isSel = p == selectedPersona
                            Button(
                                onClick = { selectedPersona = p },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSel) Color(0xFF0284C7) else Color(0xFF334155))
                            ) {
                                Text(p, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onUpdateSettings(keyInput, selectedPersona, localModelName) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("Save Configuration")
                    }
                }
            }
        }

        // CARD 2: SCHEDULED ALARMS & TELEMETRY
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⏰ Scheduled Alarms & Phone Telemetry Sync", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Phone data is mirrored to this PC over USB when you connect. It is read-only for now.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}
