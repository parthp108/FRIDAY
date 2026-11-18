package com.health.friday.alarms

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakeAlarmDao : AlarmDao {
    private val alarms = mutableListOf<Alarm>()

    override fun getAll(): Flow<List<Alarm>> = flowOf(alarms.toList())

    override suspend fun insert(alarm: Alarm): Long {
        val newId = (alarms.size + 1).toLong()
        val saved = alarm.copy(id = newId)
        alarms.add(saved)
        return newId
    }

    override suspend fun update(alarm: Alarm) {
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) alarms[index] = alarm
    }

    override suspend fun delete(alarm: Alarm) {
        alarms.removeAll { it.id == alarm.id }
    }

    override suspend fun getById(id: Long): Alarm? {
        return alarms.find { it.id == id }
    }

    override suspend fun getEnabled(): List<Alarm> {
        return alarms.filter { it.enabled }
    }

    override suspend fun disable(id: Long) {
        val alarm = getById(id)
        if (alarm != null) {
            update(alarm.copy(enabled = false))
        }
    }
}

class AutonomousWakeEngineTest {

    private lateinit var fakeContext: Context
    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var fakeAlarmDao: FakeAlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var engine: AutonomousWakeEngine

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        fakeSignalsDao = FakeDailySignalsDao()
        fakeAlarmDao = FakeAlarmDao()
        val fakeScheduler = AlarmScheduler(fakeContext)
        alarmRepository = AlarmRepository(fakeAlarmDao, fakeScheduler)
        engine = AutonomousWakeEngine(fakeSignalsDao, alarmRepository)
    }

    @Test
    fun testEarlyWakeUp_cancelsBackupAlarm() = runBlocking {
        val todayStr = LocalDate.now().toString()

        // Schedule backup alarm first
        alarmRepository.addAlarm(
            title = AutonomousWakeEngine.BACKUP_ALARM_TITLE,
            timeMillis = System.currentTimeMillis() + 3600000L,
            repeatDaily = false
        )

        // User woke up early (steps >= 50 or sleepEndEpoch recorded)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = todayStr,
                sleepStartEpoch = System.currentTimeMillis() - 28000000L,
                sleepEndEpoch = System.currentTimeMillis() - 3600000L,
                steps = 120
            )
        )

        val result = engine.evaluateMorningWakeState()

        assertEquals("User awake early — cancelled 7:00 AM backup alarm.", result)
        assertEquals(0, alarmRepository.getAlarmsNow().size)
    }

    @Test
    fun testUserStillAsleep_doesNotCancelBackupAlarm() = runBlocking {
        val todayStr = LocalDate.now().toString()

        // User still sleeping (no sleepEndEpoch, 0 steps)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = todayStr,
                sleepStartEpoch = System.currentTimeMillis() - 28000000L,
                sleepEndEpoch = null,
                steps = 0
            )
        )

        val result = engine.evaluateMorningWakeState()
        assertNull(result)
    }
}
