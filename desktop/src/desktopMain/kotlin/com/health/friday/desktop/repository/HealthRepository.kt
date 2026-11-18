package com.health.friday.desktop.repository

import com.health.friday.shared.models.DailySignalDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HealthRepository(private val onReadOnly: (String) -> Unit) {
    private val _signals = MutableStateFlow(DailySignalDto())
    val signals: StateFlow<DailySignalDto> = _signals

    fun setSignals(fetched: DailySignalDto) { _signals.value = fetched }

    @Suppress("UNUSED_PARAMETER")
    fun addWater(amountMl: Int) = onReadOnly("Logging water")
}
