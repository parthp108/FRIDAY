package com.health.friday.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.health.friday.FridayApplication
import com.health.friday.ui.components.FridayOrb
import com.health.friday.ui.components.OrbState
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayText
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceSessionLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class FridayVoiceSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionLifecycleOwner = VoiceSessionLifecycleOwner()
    private var activeAppPackage by mutableStateOf("Screen")
    private var activeWebUrl by mutableStateOf<String?>(null)
    private var screenContextText by mutableStateOf<String?>(null)
    private var extractedNodeCount by mutableIntStateOf(0)

    override fun onCreate() {
        super.onCreate()
        sessionLifecycleOwner.onCreate()
        sessionLifecycleOwner.onStart()

        // Configure system overlay window
        window?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(onCreateContentView())
    }

    override fun onCreateContentView(): View {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(sessionLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(sessionLifecycleOwner)
            setViewTreeViewModelStoreOwner(sessionLifecycleOwner)

            setContent {
                FRIDAYTheme {
                    LiveFridayOverlay(
                        appName = activeAppPackage,
                        webUrl = activeWebUrl,
                        screenContext = screenContextText,
                        nodeCount = extractedNodeCount,
                        onDismiss = { finish() }
                    )
                }
            }
        }
        return composeView
    }

    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags)
        sessionLifecycleOwner.onResume()
    }

    override fun onHide() {
        sessionLifecycleOwner.onPause()
        super.onHide()
    }

    override fun onDestroy() {
        sessionLifecycleOwner.onStop()
        sessionLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(
        state: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(state, structure, content)
        if (structure != null) {
            val pkg = structure.activityComponent?.packageName?.substringAfterLast('.')?.lowercase() ?: "app"
            activeAppPackage = when {
                pkg.contains("chrome") -> "Chrome"
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("youtube") -> "YouTube"
                pkg.contains("instagram") -> "Instagram"
                pkg.contains("maps") -> "Google Maps"
                pkg.contains("calendar") -> "Calendar"
                else -> pkg.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            val (text, count) = parseAssistStructureDetailed(structure)
            screenContextText = text
            extractedNodeCount = count
        }
        if (content != null) {
            activeWebUrl = content.webUri?.toString()
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hwImage = screenshot.copy(Bitmap.Config.HARDWARE, false)
            val hwBuffer = hwImage?.hardwareBuffer
            hwBuffer?.close()
        }
    }

    private fun parseAssistStructureDetailed(structure: AssistStructure): Pair<String, Int> {
        val sb = StringBuilder()
        var count = 0

        fun walkNode(node: AssistStructure.ViewNode?) {
            if (node == null) return
            val text = node.text?.toString()?.trim()
            val hint = node.hint?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrEmpty() || !desc.isNullOrEmpty() || !hint.isNullOrEmpty() || node.isClickable) {
                count++
                val label = text ?: desc ?: hint ?: ""
                sb.append(label).append(" ")
            }

            for (i in 0 until node.childCount) {
                walkNode(node.getChildAt(i))
            }
        }

        for (w in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(w)
            walkNode(windowNode.rootViewNode)
        }

        return Pair(sb.toString().trim(), count)
    }
}

@Composable
private fun LiveFridayOverlay(
    appName: String?,
    webUrl: String?,
    screenContext: String?,
    nodeCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val app = context.applicationContext as? FridayApplication
    val container = app?.container
    val orchestrator = container?.aiOrchestrator
    val speechRecognizer = container?.speechRecognizer
    val ttsEngine = container?.ttsEngine

    var isThinking by remember { mutableStateOf(false) }
    var responseText by remember { mutableStateOf("") }
    var userPromptInput by remember { mutableStateOf("") }

    val isListening by speechRecognizer?.isListening?.collectAsState() ?: remember { mutableStateOf(false) }
    val partialResults by speechRecognizer?.partialResults?.collectAsState() ?: remember { mutableStateOf("") }
    val rmsDb by speechRecognizer?.rmsDb?.collectAsState() ?: remember { mutableStateOf(0f) }
    val isSpeaking by ttsEngine?.isSpeaking?.collectAsState() ?: remember { mutableStateOf(false) }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-dismiss if inactive/silent for 30 seconds
    LaunchedEffect(isListening, isSpeaking, isThinking, partialResults) {
        lastInteractionTime = System.currentTimeMillis()
        while (true) {
            delay(1000)
            val elapsedSec = (System.currentTimeMillis() - lastInteractionTime) / 1000
            if (elapsedSec >= 30 && !isSpeaking && !isThinking) {
                speechRecognizer?.stopListening()
                ttsEngine?.stop()
                onDismiss()
                break
            }
        }
    }

    fun processQuery(query: String) {
        if (orchestrator == null) return
        ttsEngine?.stop()
        speechRecognizer?.stopListening()
        isThinking = true
        responseText = "Thinking..."

        scope.launch {
            try {
                val fullPrompt = if (!screenContext.isNullOrBlank()) {
                    "[On-Screen App: ${appName ?: "Screen"}, Context Text: ${screenContext.take(1200)}]\n\nUser Question: $query"
                } else {
                    query
                }
                val response = orchestrator.sendMessage(
                    message = fullPrompt,
                    section = "live_friday",
                    onPartialResponse = { partial ->
                        if (partial.isNotBlank()) {
                            responseText = partial
                        }
                    }
                )
                responseText = response
                ttsEngine?.speak(response)
            } catch (_: Exception) {
                responseText = "I couldn't process that request."
            } finally {
                isThinking = false
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) return
        ttsEngine?.stop()
        speechRecognizer.startListening(
            onResult = { finalResult ->
                if (finalResult.isNotBlank()) {
                    processQuery(finalResult)
                }
            },
            onError = { err ->
                responseText = err
            }
        )
    }

    LaunchedEffect(Unit) {
        startListening()
    }

    val orbState = when {
        isSpeaking -> OrbState.SPEAKING
        isThinking -> OrbState.THINKING
        isListening -> OrbState.LISTENING
        else -> OrbState.IDLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {} // Prevent click-through to dismiss scrim
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xFF0F141C),
            border = BorderStroke(1.dp, FridayCyan.copy(alpha = 0.4f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Bar with Gemini-Style Screen Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(FridayCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "F.R.I.D.A.Y. LIVE",
                                color = FridayCyan,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FridayCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "📱 ${appName ?: "Screen"} ${if (nodeCount > 0) "($nodeCount items)" else ""}",
                                    color = FridayCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (!webUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🌐 ${webUrl.take(20)}...",
                                    color = FridayText,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Text("✕", color = FridayText, fontSize = 18.sp)
                    }
                }

                // Animated Orb
                FridayOrb(
                    state = orbState,
                    audioLevel = rmsDb,
                    modifier = Modifier.size(72.dp)
                )

                // Transcript & Response Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161C26))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val displayText = when {
                        isListening && partialResults.isNotBlank() -> "Listening: $partialResults"
                        isListening -> "Listening... Ask about ${appName ?: "this screen"}."
                        isThinking -> responseText.ifBlank { "FRIDAY is processing..." }
                        responseText.isNotBlank() -> responseText
                        else -> "Ask FRIDAY anything about ${appName ?: "this screen"} or type below..."
                    }
                    Text(
                        text = displayText,
                        color = FridayText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Input Bar & Mic / Send Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mic Toggle Button
                    IconButton(
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                            } else {
                                startListening()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFFF5C6C) else Color(0xFF1E2634))
                    ) {
                        Text("🎤", fontSize = 20.sp)
                    }

                    // Text Input Field
                    OutlinedTextField(
                        value = userPromptInput,
                        onValueChange = { userPromptInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask FRIDAY...", color = Color(0xFF697483)) },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E2634),
                            unfocusedContainerColor = Color(0xFF1E2634),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = FridayCyan
                        )
                    )

                    // Send Query Button
                    IconButton(
                        onClick = {
                            val text = userPromptInput.trim()
                            if (text.isNotBlank()) {
                                userPromptInput = ""
                                processQuery(text)
                            }
                        },
                        enabled = userPromptInput.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (userPromptInput.isNotBlank()) FridayCyan else Color(0xFF1E2634))
                    ) {
                        Text("↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
