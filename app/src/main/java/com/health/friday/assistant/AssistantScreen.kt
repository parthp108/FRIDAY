package com.health.friday.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.health.friday.ai.AiMessage
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ui.components.FridayOrb
import com.health.friday.ui.components.OrbState
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText
import com.health.friday.vision.VisionScreen
import com.health.friday.voice.FridaySpeechRecognizer
import com.health.friday.voice.FridayTtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun launchPowerButtonGestureSettings(context: Context) {
    val intentsToTry = listOf(
        Intent("com.oneplus.settings.POWER_BUTTON_SETTINGS"),
        Intent("com.oplus.settings.POWER_BUTTON_SETTINGS"),
        Intent("android.settings.GESTURE_SETTINGS"),
        Intent("com.oneplus.setupwizard.ONEPLUS_GESTURE_SETTINGS"),
        Intent("android.settings.POWER_MENU_SETTINGS"),
        Intent("com.samsung.android.settings.SIDE_KEY_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )

    for (intent in intentsToTry) {
        if (intent.resolveActivity(context.packageManager) != null) {
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

private fun launchVoiceInputSettings(context: Context) {
    val intentsToTry = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent("android.settings.VOICE_INPUT_SETTINGS"),
        Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        Intent("com.oneplus.settings.DEFAULT_APP_SETTINGS"),
        Intent("com.oplus.settings.DEFAULT_APP_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )

    for (intent in intentsToTry) {
        if (intent.resolveActivity(context.packageManager) != null) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }
    runCatching {
        val fallback = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}

private fun launchBatterySettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

private fun launchAppPermissionSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Composable
fun AssistantScreen(
    orchestrator: AiOrchestrator,
    speechRecognizer: FridaySpeechRecognizer,
    ttsEngine: FridayTtsEngine,
    initialMessages: List<AiMessage> = emptyList(),
    autoStartLiveChat: Boolean = false,
    onMessagesChanged: (List<AiMessage>) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showVision by remember { mutableStateOf(false) }
    var showLiveChat by remember(autoStartLiveChat) { mutableStateOf(autoStartLiveChat) }
    var showPowerKeySetupDialog by remember { mutableStateOf(false) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val messages = remember(initialMessages) {
        mutableStateListOf(
            *if (initialMessages.isNotEmpty()) {
                initialMessages.toTypedArray()
            } else {
                arrayOf(
                    AiMessage(
                        role = "assistant",
                        content = "FRIDAY online. What do you need?"
                    )
                )
            }
        )
    }

    var input by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val isListening by speechRecognizer.isListening.collectAsState()
    val partialResults by speechRecognizer.partialResults.collectAsState()
    val rmsDb by speechRecognizer.rmsDb.collectAsState()
    val isSpeaking by ttsEngine.isSpeaking.collectAsState()

    val listState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showLiveChat = true
        } else {
            messages.add(AiMessage(role = "assistant", content = "Microphone permission required for Live Voice Chat."))
        }
    }

    LaunchedEffect(Unit) {
        onMessagesChanged(messages.toList())
    }

    LaunchedEffect(messages.size, isThinking) {
        onMessagesChanged(messages.toList())
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val orbState = when {
        isSpeaking -> OrbState.SPEAKING
        isThinking -> OrbState.THINKING
        isListening -> OrbState.LISTENING
        else -> OrbState.IDLE
    }

    if (showLiveChat) {
        LiveVoiceChatScreen(
            orchestrator = orchestrator,
            speechRecognizer = speechRecognizer,
            ttsEngine = ttsEngine,
            messages = messages,
            onClose = {
                speechRecognizer.stopListening()
                ttsEngine.stop()
                showLiveChat = false
            },
            modifier = modifier
        )
    } else if (showVision) {
        VisionScreen(
            orchestrator = orchestrator,
            onBack = { showVision = false },
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground)
                .navigationBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FridayOrb(
                        state = orbState,
                        audioLevel = rmsDb,
                        modifier = Modifier.size(46.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "F.R.I.D.A.Y.",
                            color = FridayText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(FridayCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ONLINE // AI ASSISTANT",
                                color = FridayCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(onClick = { showPowerKeySetupDialog = true }) {
                        Text(text = "⚙ Setup", color = FridayCyan)
                    }

                    TextButton(onClick = onOpenHistory) {
                        Text(text = "History", color = FridayCyan)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Autonomous Health & Telemetry OS",
                    color = FridayMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Conversation
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }

                if (isListening && partialResults.isNotBlank()) {
                    item {
                        Text(
                            text = "Listening: $partialResults...",
                            color = Color(0xFF697483),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            // Message input & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F15))
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Live Voice Chat Button (Gemini Live mode)
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                showLiveChat = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FridayCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Vision / Camera Button
                    Button(
                        onClick = { showVision = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141922)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Text(text = "👁", fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(text = "Ask FRIDAY...", color = Color(0xFF697483)) },
                        enabled = !isThinking && !isListening,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF141922),
                            unfocusedContainerColor = Color(0xFF141922),
                            disabledContainerColor = Color(0xFF141922),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color(0xFF697483),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF5CC8FF)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    Button(
                        onClick = {
                            val message = input.trim()
                            if (message.isEmpty()) return@Button

                            ttsEngine.stop()
                            messages.add(AiMessage(role = "user", content = message))
                            input = ""
                            isThinking = true

                            val assistantIndex = messages.size
                            messages.add(AiMessage(role = "assistant", content = "▋"))

                            scope.launch {
                                try {
                                    val response = orchestrator.sendMessage(
                                        message = message,
                                        section = "global",
                                        onPartialResponse = { partial ->
                                            if (partial.isNotBlank()) {
                                                messages[assistantIndex] = AiMessage(
                                                    role = "assistant",
                                                    content = partial
                                                )
                                            }
                                        }
                                    )
                                    messages[assistantIndex] = AiMessage(
                                        role = "assistant",
                                        content = response
                                    )
                                    ttsEngine.speak(response)
                                } catch (_: Exception) {
                                    messages[assistantIndex] = AiMessage(
                                        role = "assistant",
                                        content = "I couldn't complete that request."
                                    )
                                } finally {
                                    isThinking = false
                                }
                            }
                        },
                        enabled = input.isNotBlank() && !isThinking && !isListening,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Text(text = "↑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "FRIDAY can make mistakes. Verify important information.",
                    color = Color(0xFF4F5966),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (showPowerKeySetupDialog) {
        val isRoleHeld = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            } else {
                true
            }
        }

        AlertDialog(
            onDismissRequest = { showPowerKeySetupDialog = false },
            title = {
                Text("Power Button Assistant Setup", color = FridayText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "To trigger Live FRIDAY when long-pressing your Power Button, complete these 2 steps:",
                        color = FridayText,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141922))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isRoleHeld) "✓ Default Assistant: Active" else "⚠️ Default Assistant: Not Set",
                                color = if (isRoleHeld) FridayCyan else Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Step 1: Set FRIDAY as your Default Digital Assistant in Android settings.",
                                color = Color(0xFF9BDEFF),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141922))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "OxygenOS Troubleshooting Checklist:",
                                color = FridayCyan,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "• Step 1: Set FRIDAY as Default Assistant\n• Step 2: Set FRIDAY Battery to 'Unrestricted' (prevents OxygenOS from killing background binding)\n• Step 3: Ensure Microphone permission is set to 'Allow'\n• Step 4: Open Power Button Gesture → select 'Voice Assistant'",
                                color = Color(0xFF9BDEFF),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { launchVoiceInputSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = FridayCyan, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Set Default Assistant", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { launchBatterySettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Battery → Unrestricted", color = FridayCyan)
                    }

                    OutlinedButton(
                        onClick = { launchAppPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("3. App Permissions → Microphone", color = FridayCyan)
                    }

                    OutlinedButton(
                        onClick = { launchPowerButtonGestureSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("4. Open Gesture Settings", color = FridayCyan)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerKeySetupDialog = false }) {
                    Text("Close", color = FridayMuted)
                }
            },
            containerColor = Color(0xFF0F141C)
        )
    }
}

@Composable
private fun LiveVoiceChatScreen(
    orchestrator: AiOrchestrator,
    speechRecognizer: FridaySpeechRecognizer,
    ttsEngine: FridayTtsEngine,
    messages: SnapshotStateList<AiMessage>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var isThinking by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var currentAiResponse by remember { mutableStateOf("") }
    var currentUserText by remember { mutableStateOf("") }

    val isListening by speechRecognizer.isListening.collectAsState()
    val partialResults by speechRecognizer.partialResults.collectAsState()
    val rmsDb by speechRecognizer.rmsDb.collectAsState()
    val isSpeaking by ttsEngine.isSpeaking.collectAsState()

    var wasSpeaking by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-close if inactive for 30 seconds
    LaunchedEffect(isListening, isSpeaking, isThinking, partialResults) {
        lastInteractionTime = System.currentTimeMillis()
        while (true) {
            delay(1000)
            val elapsedSec = (System.currentTimeMillis() - lastInteractionTime) / 1000
            if (elapsedSec >= 30 && !isSpeaking && !isThinking) {
                speechRecognizer.stopListening()
                ttsEngine.stop()
                onClose()
                break
            }
        }
    }

    fun processTurn(userQuery: String) {
        if (userQuery.isBlank()) return
        currentUserText = userQuery
        currentAiResponse = ""
        isThinking = true

        messages.add(AiMessage(role = "user", content = userQuery))
        val assistantIndex = messages.size
        messages.add(AiMessage(role = "assistant", content = "▋"))

        scope.launch {
            try {
                val response = orchestrator.sendMessage(
                    message = userQuery,
                    section = "live_voice_chat",
                    onPartialResponse = { partial ->
                        if (partial.isNotBlank()) {
                            currentAiResponse = partial
                            messages[assistantIndex] = AiMessage(
                                role = "assistant",
                                content = partial
                            )
                        }
                    }
                )
                currentAiResponse = response
                messages[assistantIndex] = AiMessage(
                    role = "assistant",
                    content = response
                )
                ttsEngine.speak(response)
            } catch (_: Exception) {
                messages[assistantIndex] = AiMessage(
                    role = "assistant",
                    content = "I couldn't process that request."
                )
            } finally {
                isThinking = false
            }
        }
    }

    fun startListeningTurn() {
        if (isMuted || isThinking || isSpeaking) return
        speechRecognizer.startListening(
            onResult = { finalResult ->
                if (finalResult.isNotBlank()) {
                    processTurn(finalResult)
                } else if (!isMuted && !isThinking) {
                    scope.launch {
                        delay(200)
                        startListeningTurn()
                    }
                }
            },
            onError = { _ ->
                if (!isMuted && !isThinking && !isSpeaking) {
                    scope.launch {
                        delay(500)
                        startListeningTurn()
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        startListeningTurn()
    }

    LaunchedEffect(isSpeaking) {
        if (wasSpeaking && !isSpeaking && !isMuted && !isThinking) {
            delay(400)
            startListeningTurn()
        }
        wasSpeaking = isSpeaking
    }

    val orbState = when {
        isSpeaking -> OrbState.SPEAKING
        isThinking -> OrbState.THINKING
        isListening -> OrbState.LISTENING
        else -> OrbState.IDLE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color(0xFFFF5C6C) else FridayCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMuted) "LIVE CHAT PAUSED" else "F.R.I.D.A.Y. LIVE",
                    color = FridayCyan,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Button(
                onClick = onClose,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2330)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("End Chat ✕", color = Color.White, fontSize = 13.sp)
            }
        }

        // Center Orb & Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FridayOrb(
                state = orbState,
                audioLevel = rmsDb,
                modifier = Modifier.size(180.dp)
            )

            val statusText = when {
                isMuted -> "Muted. Tap mic to resume."
                isSpeaking -> "FRIDAY is speaking..."
                isThinking -> "FRIDAY is thinking..."
                isListening && partialResults.isNotBlank() -> partialResults
                isListening -> "Listening... speak now"
                else -> "Ready for voice chat"
            }

            Text(
                text = statusText,
                color = FridayCyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Live Transcript Surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 160.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF121822),
            border = BorderStroke(1.dp, FridayCyan.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentUserText.isNotBlank()) {
                    Text(
                        text = "You: $currentUserText",
                        color = Color(0xFF9BDEFF),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (currentAiResponse.isNotBlank()) {
                    Text(
                        text = "FRIDAY: $currentAiResponse",
                        color = FridayText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (currentUserText.isBlank() && !isListening) {
                    Text(
                        text = "Speak naturally with FRIDAY in real time.",
                        color = Color(0xFF697483),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute / Resume Button
            Button(
                onClick = {
                    isMuted = !isMuted
                    if (isMuted) {
                        speechRecognizer.stopListening()
                        ttsEngine.stop()
                    } else {
                        startListeningTurn()
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMuted) Color(0xFFFF5C6C) else Color(0xFF1B2330)
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Text(if (isMuted) "🔇" else "🎙", fontSize = 24.sp)
            }

            // Stop Speaking Button
            Button(
                onClick = {
                    ttsEngine.stop()
                    if (!isMuted) {
                        startListeningTurn()
                    }
                },
                enabled = isSpeaking || isThinking,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2330)),
                modifier = Modifier.size(64.dp)
            ) {
                Text("⏹", fontSize = 22.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AiMessage) {
    val isUser = message.role == "user"
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.92f)
                .clip(RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 18.dp
                ))
                .background(if (isUser) Color(0xFF1B4F68) else Color(0xFF121720))
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (isUser) "YOU" else "FRIDAY",
                color = if (isUser) Color(0xFF9BDEFF) else Color(0xFF5CC8FF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = message.content,
                color = Color(0xFFE9EDF2),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = timeFormat.format(Date(message.createdAt)),
                color = Color(0xFF697483),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
