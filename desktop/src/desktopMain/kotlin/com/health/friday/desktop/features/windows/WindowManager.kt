package com.health.friday.desktop.features.windows

import com.health.friday.desktop.features.widgets.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WindowManager {
    // Keeps track of which widgets are currently detached into their own windows
    private val _detachedWidgets = MutableStateFlow<Set<WidgetConfig>>(emptySet())
    val detachedWidgets: StateFlow<Set<WidgetConfig>> = _detachedWidgets.asStateFlow()

    fun detachWidget(config: WidgetConfig) {
        _detachedWidgets.value = _detachedWidgets.value + config
    }

    fun closeDetachedWindow(config: WidgetConfig) {
        _detachedWidgets.value = _detachedWidgets.value - config
    }
}
