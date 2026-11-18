package com.health.friday.desktop.features.layout

import com.health.friday.desktop.features.widgets.WidgetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AdaptiveLayoutState {
    // Tracks which widgets the user has explicitly pinned to the top
    private val _pinnedWidgets = MutableStateFlow<Set<WidgetType>>(emptySet())
    val pinnedWidgets: StateFlow<Set<WidgetType>> = _pinnedWidgets.asStateFlow()

    // Toggles whether the dashboard is in "Deep Focus" mode
    private val _isFocusMode = MutableStateFlow(false)
    val isFocusMode: StateFlow<Boolean> = _isFocusMode.asStateFlow()

    fun togglePin(widget: WidgetType) {
        _pinnedWidgets.update { current ->
            if (current.contains(widget)) current - widget else current + widget
        }
    }

    fun toggleFocusMode() {
        _isFocusMode.value = !_isFocusMode.value
    }
}
