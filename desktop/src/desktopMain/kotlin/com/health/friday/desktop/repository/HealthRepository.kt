package com.health.friday.desktop.repository

import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.HydrationLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HealthRepository(private val onReadOnly: (String) -> Unit) {
    private val _signals = MutableStateFlow(DailySignalDto())
    val signals: StateFlow<DailySignalDto> = _signals
    private var phoneSignals = DailySignalDto()
    private var cloudSignals: DailySignalDto? = null

    private val _cloudWaterEntries = MutableStateFlow<List<HydrationLogDto>>(emptyList())
    val cloudWaterEntries: StateFlow<List<HydrationLogDto>> = _cloudWaterEntries

    @Synchronized
    fun setSignals(fetched: DailySignalDto) {
        phoneSignals = fetched
        publishSignals()
    }

    @Synchronized
    fun setCloudSignals(fetched: DailySignalDto?) {
        cloudSignals = fetched
        publishSignals()
    }

    fun setCloudWaterEntries(fetched: List<HydrationLogDto>) {
        _cloudWaterEntries.value = fetched
    }

    private fun publishSignals() {
        val cloud = cloudSignals
        _signals.value = when {
            cloud == null -> phoneSignals
            cloud.date > phoneSignals.date -> cloud
            cloud.date < phoneSignals.date -> phoneSignals
            cloud.timestamp > phoneSignals.timestamp -> cloud
            else -> phoneSignals
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun addWater(amountMl: Int) = onReadOnly("Logging water")
}
