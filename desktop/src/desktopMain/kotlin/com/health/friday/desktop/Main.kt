package com.health.friday.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import cafe.adriel.voyager.navigator.Navigator
import com.health.friday.desktop.daemon.BootConfigurator
import com.health.friday.desktop.daemon.HeadlessDaemon
import com.health.friday.desktop.di.appModule
import com.health.friday.desktop.features.command.CommandSurfaceOverlay
import com.health.friday.desktop.features.overlay.AssistantOverlayWindow
import com.health.friday.desktop.features.widgets.HealthVitalsWidget
import com.health.friday.desktop.features.widgets.WidgetType
import com.health.friday.desktop.features.widgets.ZeroLatencyStreamWidget
import com.health.friday.desktop.features.windows.WindowManager
import com.health.friday.desktop.network.TelemetryClient
import com.health.friday.desktop.theme.AmbientState
import com.health.friday.desktop.theme.FridayTheme
import com.health.friday.desktop.ui.DesktopHudScreen
import com.health.friday.desktop.ui.components.AmbientLayer
import com.health.friday.shared.models.DailySignalDto
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.awt.SystemTray

fun main() {
    BootConfigurator.ensureRunOnStartup()

    startKoin {
        modules(appModule)
    }

    val daemon = GlobalContext.get().get<HeadlessDaemon>()

    application {
        var isMainWindowVisible by remember { mutableStateOf(true) }
        var isOverlayVisible by remember { mutableStateOf(false) }
        var isCommandSurfaceVisible by remember { mutableStateOf(false) }
        var isHudOverlayMode by remember { mutableStateOf(false) }
        var currentAmbientState by remember { mutableStateOf(AmbientState.IDLE) }

        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        val isTraySupported = remember { SystemTray.isSupported() }
        val appIcon = painterResource("icon.png")

        val windowManager = remember { GlobalContext.get().get<WindowManager>() }
        val detachedWidgets by windowManager.detachedWidgets.collectAsState()
        val dummyClient = remember { TelemetryClient("127.0.0.1") }

        // Ambient state breathing pulse simulation
        LaunchedEffect(Unit) {
            while (true) {
                delay(10000)
                currentAmbientState = AmbientState.THINKING
                delay(6000)
                currentAmbientState = AmbientState.IDLE
            }
        }

        // Global OS Hotkey Listener (Alt + Space)
        LaunchedEffect(Unit) {
            daemon.hotkeyFlow.collect { pressed ->
                if (pressed) {
                    isCommandSurfaceVisible = !isCommandSurfaceVisible
                }
            }
        }

        if (isTraySupported && appIcon != null) {
            Tray(
                icon = appIcon,
                tooltip = "FRIDAY ECo (Daemon Running - Alt+Space to Summon)",
                onAction = {
                    isMainWindowVisible = true
                    windowState.isMinimized = false
                },
                menu = {
                    Item(if (isMainWindowVisible) "Hide Dashboard" else "Show Dashboard (Alt+Space)", onClick = {
                        isMainWindowVisible = !isMainWindowVisible
                        if (isMainWindowVisible) {
                            windowState.isMinimized = false
                        }
                    })
                    Item(
                        text = if (isOverlayVisible) "Hide Floating Overlay" else "Show Floating Overlay",
                        onClick = { isOverlayVisible = !isOverlayVisible }
                    )
                    Separator()
                    Item("Quit Daemon", onClick = {
                        daemon.stop()
                        exitApplication()
                    })
                }
            )
        }

        if (isMainWindowVisible) {
            Window(
                onCloseRequest = {
                    if (isTraySupported) {
                        isMainWindowVisible = false
                    } else {
                        windowState.isMinimized = true
                    }
                },
                title = "F.R.I.D.A.Y. Desktop (USB Live)",
                icon = appIcon,
                state = windowState,
                alwaysOnTop = isHudOverlayMode
            ) {
                MenuBar {
                    Menu("View") {
                        Item("Toggle Floating Overlay", shortcut = KeyShortcut(Key.O, ctrl = true)) {
                            isOverlayVisible = !isOverlayVisible
                        }
                    }
                }

                FridayTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AmbientLayer(
                            state = currentAmbientState,
                            modifier = Modifier.fillMaxSize()
                        )
                        Navigator(DesktopHudScreen())
                    }
                }
            }
        }

        // Dynamically Spawn Detached Native OS Windows
        detachedWidgets.forEach { config ->
            Window(
                onCloseRequest = { windowManager.closeDetachedWindow(config) },
                title = "FRIDAY - ${config.type.name}",
                icon = appIcon,
                state = rememberWindowState(width = 420.dp, height = config.defaultHeight)
            ) {
                FridayTheme {
                    when (config.type) {
                        WidgetType.HEALTH_VITALS -> HealthVitalsWidget(DailySignalDto())
                        WidgetType.TELEMETRY_SPARKLINE -> ZeroLatencyStreamWidget(dummyClient, isConnected = false)
                        else -> {}
                    }
                }
            }
        }

        if (isOverlayVisible) {
            AssistantOverlayWindow(
                onClose = { isOverlayVisible = false }
            )
        }

        if (isCommandSurfaceVisible) {
            Window(
                onCloseRequest = { isCommandSurfaceVisible = false },
                transparent = true,
                undecorated = true,
                alwaysOnTop = true,
                state = rememberWindowState(placement = WindowPlacement.Fullscreen)
            ) {
                FridayTheme {
                    CommandSurfaceOverlay(
                        isVisible = isCommandSurfaceVisible,
                        onClose = { isCommandSurfaceVisible = false },
                        onActionSelected = { actionId ->
                            println("Executing: $actionId")
                            isCommandSurfaceVisible = false
                        }
                    )
                }
            }
        }
    }
}
