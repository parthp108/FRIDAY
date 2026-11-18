package com.health.friday.desktop.daemon

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

class HeadlessDaemon : NativeKeyListener {

    private val _hotkeyFlow = MutableSharedFlow<Boolean>()
    val hotkeyFlow: SharedFlow<Boolean> = _hotkeyFlow.asSharedFlow()

    private val daemonScope = CoroutineScope(Dispatchers.IO)
    private var isAltPressed = false

    fun start() {
        // Disable JNativeHook's aggressive console logging
        Logger.getLogger(GlobalScreen::class.java.`package`.name).level = Level.WARNING

        try {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(this)
        } catch (ex: Exception) {
            println("[HeadlessDaemon] Failed to register native OS hooks: ${ex.message}")
        }
    }

    fun stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this)
            GlobalScreen.unregisterNativeHook()
        } catch (ex: Exception) {
            // Ignore
        }
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        if (e.keyCode == NativeKeyEvent.VC_ALT) {
            isAltPressed = true
        } else if (e.keyCode == NativeKeyEvent.VC_SPACE && isAltPressed) {
            // Trigger the summon command when Alt + Space is pressed anywhere in the OS
            daemonScope.launch {
                _hotkeyFlow.emit(true)
            }
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        if (e.keyCode == NativeKeyEvent.VC_ALT) {
            isAltPressed = false
        }
    }

    override fun nativeKeyTyped(e: NativeKeyEvent) {}
}
