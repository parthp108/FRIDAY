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
import com.health.friday.desktop.sync.cloud.DesktopSyncState
import com.health.friday.desktop.sync.cloud.DesktopSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatRelativeTime(ms: Long): String {
    if (ms <= 0L) return "Never"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}

@Composable
fun DesktopSettingsScreen(
    apiKey: String,
    personaStyle: String,
    selectedLocalModel: String = "llama3.2:1b",
    isDriveConnected: Boolean = false,
    lastSyncMs: Long = 0L,
    driveSyncStatus: DesktopSyncStatus = DesktopSyncStatus(),
    onUpdateSettings: (String, String, String) -> Unit,
    onDriveConnect: (java.io.File) -> String = { error("Drive connection is not wired.") },
    onDriveBackup: (CharArray) -> String = { error("Drive key backup is not wired.") },
    onDriveRestore: (CharArray) -> String = { error("Drive key restore is not wired.") },
    onDriveSync: suspend () -> com.health.friday.desktop.sync.cloud.DesktopDriveSyncResult = { error("Drive sync is not wired.") }
) {
    var keyInput by remember { mutableStateOf(apiKey) }
    var selectedPersona by remember { mutableStateOf(personaStyle) }
    var localModelName by remember { mutableStateOf(selectedLocalModel) }
    var localLlmEnabled by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    var syncRunning by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var passphraseInput by remember { mutableStateOf("") }
    var confirmPassphraseInput by remember { mutableStateOf("") }
    var showFilePicker by remember { mutableStateOf(false) }

    if (showFilePicker) {
        LaunchedEffect(Unit) {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Google OAuth credentials JSON", java.awt.FileDialog.LOAD).apply {
                file = "*.json"
                isVisible = true
            }
            val file = dialog.file?.let { java.io.File(dialog.directory, it) }
            showFilePicker = false
            if (file != null) {
                syncRunning = true
                actionStatus = "Opening browser for Google authorization..."
                val result = runCatching { withContext(Dispatchers.IO) { onDriveConnect(file) } }
                actionStatus = result.fold(
                    onSuccess = { it },
                    onFailure = { "CONNECT FAILED: ${it.message ?: it}" }
                )
                syncRunning = false
            }
        }
    }

    val displayStatus = when {
        syncRunning || driveSyncStatus.state == DesktopSyncState.SYNCING -> "SYNCING: Encrypted data exchange in progress..."
        actionStatus != null -> actionStatus!!
        driveSyncStatus.state == DesktopSyncState.AUTH_REQUIRED -> "RECONNECT REQUIRED: Google Drive access requires authorization."
        driveSyncStatus.state == DesktopSyncState.FAILED -> "SYNC FAILED: ${driveSyncStatus.errorMessage.orEmpty()}"
        isDriveConnected -> "STATUS: CONNECTED & ENCRYPTED VAULT READY\nLast sync: ${formatRelativeTime(driveSyncStatus.lastSyncMs.takeIf { it > 0 } ?: lastSyncMs)}"
        else -> "Not connected to Google Drive"
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // CARD 0: ENCRYPTED GOOGLE DRIVE RECOVERY & SYNC
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
                        "Encrypted Google Drive Recovery & Sync",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        "Connect this Windows installation to Google Drive, back up a new recovery phrase, or restore your key using Android's recovery phrase.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !syncRunning,
                            onClick = { showFilePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text(if (isDriveConnected) "Reconnect Google Drive" else "1. Connect Google Drive")
                        }
                    }

                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Recovery phrase (16+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        )
                    )

                    OutlinedTextField(
                        value = confirmPassphraseInput,
                        onValueChange = { confirmPassphraseInput = it },
                        label = { Text("Confirm recovery phrase (for backup)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !syncRunning && passphraseInput.length >= 16 && passphraseInput == confirmPassphraseInput,
                            onClick = {
                                val phrase = passphraseInput.toCharArray()
                                syncRunning = true
                                actionStatus = "Backing up recovery key to Google Drive..."

                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            onDriveBackup(phrase)
                                        }
                                    }

                                    actionStatus = result.fold(
                                        onSuccess = { it },
                                        onFailure = { "BACKUP FAILED: ${it.message ?: it}" }
                                    )
                                    passphraseInput = ""
                                    confirmPassphraseInput = ""
                                    syncRunning = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text("2. Back Up Recovery Key")
                        }

                        Button(
                            enabled = !syncRunning && passphraseInput.length >= 16,
                            onClick = {
                                val phrase = passphraseInput.toCharArray()
                                syncRunning = true
                                actionStatus = "Restoring key from Google Drive..."

                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            onDriveRestore(phrase)
                                        }
                                    }

                                    actionStatus = result.fold(
                                        onSuccess = { it },
                                        onFailure = { "RESTORE FAILED: ${it.message ?: it}" }
                                    )
                                    passphraseInput = ""
                                    confirmPassphraseInput = ""
                                    syncRunning = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text("3. Restore Key on Windows")
                        }
                    }

                    Button(
                        enabled = !syncRunning,
                        onClick = {
                            syncRunning = true
                            actionStatus = "Synchronizing encrypted batches..."

                            scope.launch {
                                val result = runCatching {
                                    onDriveSync()
                                }

                                actionStatus = result.fold(
                                    onSuccess = { res ->
                                        "SYNC COMPLETED\nUploaded batches: ${res.uploadedBatches}\nUploaded operations: ${res.uploadedOperations}\nDownloaded batches: ${res.downloadedBatches}\nApplied operations: ${res.appliedOperations}\nDuplicate/stale operations: ${res.duplicateOrStaleOperations}"
                                    },
                                    onFailure = { "SYNC FAILED: ${it.message ?: it}" }
                                )
                                syncRunning = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text(if (syncRunning || driveSyncStatus.state == DesktopSyncState.SYNCING) "Working..." else "Sync Now")
                    }

                    Text(
                        displayStatus,
                        color = Color(0xFFCBD5E1),
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
                    Text("Data is synced across devices via encrypted Google Drive Vault.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}
