package com.health.friday.proactive

import com.health.friday.data.local.DailySignalsDao
import com.health.friday.data.local.DailySignalsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeProactiveDao : ProactiveDao {
    val nudges = mutableListOf<NudgeHistoryEntity>()
    val protocols = mutableListOf<ProtocolEntity>()
    private var nudgeIdCounter = 1L

    override suspend fun insertNudge(nudge: NudgeHistoryEntity): Long {
        val inserted = nudge.copy(id = nudgeIdCounter++)
        nudges.add(inserted)
        return inserted.id
    }

    override suspend fun updateNudge(nudge: NudgeHistoryEntity) {
        val index = nudges.indexOfFirst { it.id == nudge.id }
        if (index >= 0) nudges[index] = nudge
    }

    override suspend fun getNudgeById(id: Long): NudgeHistoryEntity? {
        return nudges.find { it.id == id }
    }

    override suspend fun getNudgesSince(since: Long): List<NudgeHistoryEntity> {
        return nudges.filter { it.timestamp >= since }
    }

    override suspend fun getLastNudgeOfType(type: String): NudgeHistoryEntity? {
        return nudges.filter { it.type == type }.maxByOrNull { it.timestamp }
    }

    override suspend fun getRecentNudgesOfType(type: String, limit: Int): List<NudgeHistoryEntity> {
        return nudges.filter { it.type == type }.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun insertProtocol(protocol: ProtocolEntity): Long {
        val inserted = protocol.copy(id = if (protocol.id == 0L) (protocols.size + 1).toLong() else protocol.id)
        protocols.add(inserted)
        return inserted.id
    }

    override suspend fun getActiveProtocols(): List<ProtocolEntity> = protocols.filter { it.isEnabled }
    override suspend fun getProtocolById(id: Long): ProtocolEntity? = protocols.find { it.id == id }
    override suspend fun getProtocolCount(): Int = protocols.size
}

class FakeDailySignalsDao : DailySignalsDao {
    val signalsMap = mutableMapOf<String, DailySignalsEntity>()

    override suspend fun insertOrUpdate(signal: DailySignalsEntity) {
        signalsMap[signal.date] = signal
    }

    override suspend fun getSignalForDate(date: String): DailySignalsEntity? {
        return signalsMap[date]
    }

    override fun observeSignalForDate(date: String): Flow<DailySignalsEntity?> {
        return flowOf(signalsMap[date])
    }

    override suspend fun getRecentSignals(limit: Int): List<DailySignalsEntity> {
        return signalsMap.values.sortedByDescending { it.date }.take(limit)
    }

    override fun getRecentSignalsFlow(): Flow<List<DailySignalsEntity>> {
        return flowOf(signalsMap.values.sortedByDescending { it.date }.take(14))
    }

    override suspend fun getSignalsInRange(startDate: String, endDate: String): List<DailySignalsEntity> {
        return signalsMap.values.filter { it.date in startDate..endDate }.sortedBy { it.date }
    }

    override suspend fun updateMood(date: String, score: Int): Int {
        val existing = signalsMap[date] ?: return 0
        signalsMap[date] = existing.copy(moodScore = score)
        return 1
    }
}
