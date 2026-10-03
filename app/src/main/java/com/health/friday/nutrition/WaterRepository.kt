package com.health.friday.nutrition

import com.health.friday.data.local.WaterDao
import com.health.friday.data.local.WaterEntry
import com.health.friday.device.HealthConnectRepository
import com.health.friday.util.DayRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

class WaterRepository(
    private val waterDao: WaterDao,
    private val healthConnectRepository: HealthConnectRepository
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayEntries(): Flow<List<WaterEntry>> {
        return DayRange.todayFlow().flatMapLatest { range ->
            waterDao.getEntriesForDay(
                startOfDay = range.first,
                endOfDay = range.second
            )
        }
    }

    fun observeEntriesForDay(
        dayStart: Long
    ): Flow<List<WaterEntry>> {
        return waterDao.getEntriesForDay(
            startOfDay = dayStart,
            endOfDay = DayRange.shiftDays(
                dayStart,
                1
            )
        )
    }

    suspend fun addWater(
        amountMl: Int,
        timestamp: Long = System.currentTimeMillis()
    ) {

        val entry =
            WaterEntry(
                amountMl = amountMl,
                timestamp = timestamp
            )

        val waterId =
            waterDao.insertWater(entry)

        try {

            healthConnectRepository.writeWater(
                waterId = waterId,
                amountMl = amountMl,
                timestamp = timestamp
            )

        } catch (_: Exception) {
            // Keep FRIDAY's local entry if Health Connect
            // is temporarily unavailable.
        }
    }

    suspend fun getEntriesBetween(
        startMillis: Long,
        endMillis: Long
    ): List<WaterEntry> {

        return waterDao
            .getEntriesForDay(
                startOfDay = startMillis,
                endOfDay = endMillis
            )
            .first()
    }

    suspend fun deleteWater(
        entry: WaterEntry
    ) {

        waterDao.deleteWater(entry)

        try {

            healthConnectRepository.deleteWater(
                waterId = entry.id
            )

        } catch (_: Exception) {
            // Local FRIDAY deletion remains successful.
        }
    }

    suspend fun getTodayTotalMl(): Int {

        val range = DayRange.today()

        return waterDao
            .getTodayWater(
                startOfDay = range.first,
                endOfDay = range.second
            )
            .first()
    }
}