package com.health.friday.proactive

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.FridayApplication
import com.health.friday.ai.AiMessage
import com.health.friday.di.AppContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProactiveVoiceActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MESSAGE = "extra_proactive_message"
        const val EXTRA_NUDGE_TYPE = "extra_nudge_type"
        const val EXTRA_NUDGE_ID = "extra_nudge_id"

        fun launch(context: Context, message: String, nudgeType: String, nudgeId: Long = -1L) {
            val intent = Intent(context, ProactiveVoiceActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_NUDGE_TYPE, nudgeType)
                putExtra(EXTRA_NUDGE_ID, nudgeId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    private val emotionalEngine = EmotionalReactionEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = applicationContext as FridayApplication
        val container = app.container

        val proactiveMessage = intent.getStringExtra(EXTRA_MESSAGE) ?: "Hey boss, quick check-in!"
        val nudgeType = intent.getStringExtra(EXTRA_NUDGE_TYPE) ?: "proactive_check"
        val nudgeId = intent.getLongExtra(EXTRA_NUDGE_ID, -1L)

        setContent {
            ProactiveVoiceScreen(
                proactiveMessage = proactiveMessage,
                appContainer = container,
                emotionalEngine = emotionalEngine,
                nudgeType = nudgeType,
                nudgeId = nudgeId,
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun ProactiveVoiceScreen(
    proactiveMessage: String,
    appContainer: AppContainer,
    emotionalEngine: EmotionalReactionEngine,
    nudgeType: String,
    nudgeId: Long,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val chatMessages = remember {
        mutableStateListOf(AiMessage(role = "assistant", content = proactiveMessage))
    }

    var statusText by remember { mutableStateOf("FRIDAY Speaking...") }
    var isProcessing by remember { mutableStateOf(false) }
    var isVentingMode by remember { mutableStateOf(false) }
    var isVentingActiveState by remember { mutableStateOf(true) }

    val isListening by appContainer.speechRecognizer.isListening.collectAsState()
    val partialResults by appContainer.speechRecognizer.partialResults.collectAsState()

    val cyanColor = Color(0xFF00E5FF)
    val darkBg = Color(0xF10B0E14)

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        // Step 1: Speak initial proactive prompt
        statusText = "FRIDAY Speaking..."
        appContainer.ttsEngine.speak(proactiveMessage)

        delay(400)
        while (appContainer.ttsEngine.isSpeaking.value) {
            delay(100)
        }

        var isBedtimePendingJournal = false
        var keepConversationAlive = true

        while (keepConversationAlive) {
            // Step 2: 3-Minute Live Session Window Loop
            val startTime = System.currentTimeMillis()
            val totalListenTimeoutMs = 180000L // 3 Minutes Live Session
            var hasUserSpokenThisTurn = false

            while (!hasUserSpokenThisTurn && (System.currentTimeMillis() - startTime) < totalListenTimeoutMs) {
                val remainingSec = maxOf(1, ((totalListenTimeoutMs - (System.currentTimeMillis() - startTime)) / 1000).toInt())
                statusText = if (isBedtimePendingJournal) {
                    "Listening for journal entry... (${remainingSec}s)"
                } else {
                    "Listening... (${remainingSec}s)"
                }

                val resultDeferred = CompletableDeferred<String?>()

                appContainer.speechRecognizer.startListening(
                    isLongForm = isBedtimePendingJournal,
                    onResult = { text -> resultDeferred.complete(text) },
                    onError = { _ -> resultDeferred.complete(null) }
                )

                val recognizedText = resultDeferred.await()

                if (!recognizedText.isNullOrBlank()) {
                    hasUserSpokenThisTurn = true
                    chatMessages.add(AiMessage(role = "user", content = recognizedText))

                    // Evaluate Emotion / Intent
                    statusText = "Evaluating response..."
                    when (val reaction = emotionalEngine.evaluateReaction(recognizedText)) {
                        is EmotionalReactionEngine.ReactionResult.Dismissed -> {
                            statusText = "Closing turn..."
                            val apology = reaction.apologyText.ifBlank { "Understood boss!" }
                            chatMessages.add(AiMessage(role = "assistant", content = apology))
                            appContainer.ttsEngine.speak(apology)
                            delay(1800)
                            if (nudgeId != -1L) {
                                appContainer.nudgePolicy.markInteracted(nudgeId)
                            }
                            keepConversationAlive = false
                            break
                        }
                        is EmotionalReactionEngine.ReactionResult.Engaged -> {
                            isProcessing = true
                            statusText = "FRIDAY Processing..."

                            if (nudgeId != -1L) {
                                appContainer.nudgePolicy.markInteracted(nudgeId)
                            } else {
                                appContainer.nudgePolicy.markInteracted(nudgeType)
                            }

                            // Check for Postpone / Delay Request in Bedtime Journal Nudge
                            val cleanSpeech = reaction.userText.lowercase()
                            val isPostpone = listOf("postpone", "wait", "later", "hour", "30 min", "working", "work more", "delay", "push back").any { cleanSpeech.contains(it) }

                            if (nudgeType == "bedtime_journal" && isPostpone) {
                                val delayMins = if (cleanSpeech.contains("30")) 30L else 60L
                                ProactiveBedtimeJournalWorker.scheduleInMinutes(context, delayMins)

                                val postponeReply = "Understood boss! Pushing bedtime back by $delayMins minutes. I'll check back in with you then!"
                                chatMessages.add(AiMessage(role = "assistant", content = postponeReply))
                                statusText = "Bedtime Postponed"
                                isProcessing = false

                                appContainer.ttsEngine.speak(postponeReply)
                                delay(400)
                                while (appContainer.ttsEngine.isSpeaking.value) {
                                    delay(100)
                                }
                                delay(1800)
                                keepConversationAlive = false
                                break
                            }

                            // Dedicated Bedtime Journaling & Long-Form Venting Mode
                            if (nudgeType == "bedtime_journal") {
                                val initialSpeech = if (listOf("yes", "ok", "okay", "yeah", "sure", "ready", "let's go").any { cleanSpeech.contains(it) }) "" else reaction.userText

                                if (initialSpeech.isBlank()) {
                                    val journalPrompt = "Awesome, boss! How was your day today? Feel free to speak your journal entry."
                                    chatMessages.add(AiMessage(role = "assistant", content = journalPrompt))
                                    statusText = "FRIDAY Prompting..."
                                    isProcessing = false

                                    appContainer.ttsEngine.speak(journalPrompt)
                                    delay(400)
                                    while (appContainer.ttsEngine.isSpeaking.value) {
                                        delay(100)
                                    }
                                    delay(600)
                                }

                                isVentingMode = true
                                isVentingActiveState = true
                                statusText = "Venting & Journaling Active • Speak freely..."

                                val accumulatedJournal = StringBuilder(initialSpeech)
                                if (initialSpeech.isNotBlank()) {
                                    chatMessages.add(AiMessage(role = "user", content = initialSpeech))
                                } else {
                                    chatMessages.add(AiMessage(role = "user", content = "Listening..."))
                                }

                                appContainer.speechRecognizer.startContinuousSession(
                                    onSegmentRecognized = { segment ->
                                        if (segment.isNotBlank()) {
                                            if (accumulatedJournal.isNotEmpty() && !accumulatedJournal.endsWith(" ")) {
                                                accumulatedJournal.append(" ")
                                            }
                                            accumulatedJournal.append(segment)

                                            if (chatMessages.isNotEmpty() && chatMessages.last().role == "user") {
                                                chatMessages[chatMessages.size - 1] = AiMessage(role = "user", content = accumulatedJournal.toString())
                                            }
                                        }
                                    },
                                    onPartialText = { partial ->
                                        val currentFull = accumulatedJournal.toString() + (if (accumulatedJournal.isNotEmpty() && !accumulatedJournal.endsWith(" ")) " " else "") + partial
                                        if (chatMessages.isNotEmpty() && chatMessages.last().role == "user") {
                                            chatMessages[chatMessages.size - 1] = AiMessage(role = "user", content = currentFull)
                                        }
                                    },
                                    onError = { _ -> }
                                )

                                // Stay active continuously while user vents
                                while (isVentingActiveState) {
                                    delay(200)
                                }

                                appContainer.speechRecognizer.stopContinuousSession()
                                isVentingMode = false

                                val fullJournalText = accumulatedJournal.toString().trim()
                                if (fullJournalText.isNotBlank() && fullJournalText != "Listening...") {
                                    statusText = "Saving to Journal Database..."

                                    val journalConfirm = withContext(Dispatchers.IO) {
                                        val saved = appContainer.journalRepository.saveEntry(fullJournalText)
                                        Log.i("ProactiveVoiceActivity", "Saved voice journal entry ID: ${saved?.id}")

                                        runCatching {
                                            appContainer.aiOrchestrator.generateJournalFeedback(fullJournalText)
                                        }.getOrDefault("Logged your journal entry for today, boss. Rest well tonight!")
                                    }

                                    chatMessages.add(AiMessage(role = "assistant", content = journalConfirm))
                                    isProcessing = false
                                    statusText = "Saved to Journal!"

                                    appContainer.ttsEngine.speak(journalConfirm)
                                    delay(400)
                                    while (appContainer.ttsEngine.isSpeaking.value) {
                                        delay(100)
                                    }
                                    delay(1800)
                                }
                                keepConversationAlive = false
                                break
                            }

                            // General Proactive AI Response (Water, Meals, Tasks, etc.)
                            val aiResponse = withContext(Dispatchers.IO) {
                                appContainer.aiOrchestrator.sendMessage(
                                    message = reaction.userText,
                                    section = "proactive_voice"
                                )
                            }

                            chatMessages.add(AiMessage(role = "assistant", content = aiResponse))
                            statusText = "FRIDAY Replying..."
                            isProcessing = false

                            appContainer.ttsEngine.speak(aiResponse)
                            delay(400)
                            while (appContainer.ttsEngine.isSpeaking.value) {
                                delay(100)
                            }
                            delay(1500)
                            keepConversationAlive = false
                            onDismiss()
                            break
                        }
                    }
                } else {
                    delay(400)
                }
            }

            if (!hasUserSpokenThisTurn) {
                statusText = "Conversation ended"
                keepConversationAlive = false
            }
        }

        delay(1200)
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            appContainer.speechRecognizer.stopListening()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .border(1.dp, cyanColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121824))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color.Green else cyanColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "F.R.I.D.A.Y. // PROACTIVE ASSIST",
                        color = cyanColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Conversation List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { msg ->
                        val isUser = msg.role == "user"
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isUser) Color(0xFFB0BEC5) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium,
                                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isUser) Color(0xFF1E2838) else Color(0xFF1A2332))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User Recognized Speech / Partial Results
                if (partialResults.isNotBlank()) {
                    Text(
                        text = "\"$partialResults\"",
                        color = Color(0xFF80D8FF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Status Indicator
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = cyanColor,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        text = statusText,
                        color = cyanColor.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isVentingMode) {
                        Button(
                            onClick = {
                                isVentingActiveState = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cyanColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Finish & Save Journal", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            isVentingActiveState = false
                            appContainer.ttsEngine.stop()
                            appContainer.speechRecognizer.stopListening()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Dismiss", color = Color.White)
                    }
                }
            }
        }
    }
}
