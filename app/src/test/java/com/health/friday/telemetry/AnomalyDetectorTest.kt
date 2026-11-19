package com.health.friday.telemetry

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import com.health.friday.proactive.FakeProactiveDao
import com.health.friday.proactive.NudgePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AnomalyDetectorTest {

    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var fakeProactiveDao: FakeProactiveDao
    private lateinit var nudgePolicy: NudgePolicy
    private lateinit var fakeContext: Context
    private lateinit var anomalyDetector: AnomalyDetector

    @Before
    fun setUp() {
        fakeSignalsDao = FakeDailySignalsDao()
        fakeProactiveDao = FakeProactiveDao()
        nudgePolicy = NudgePolicy(fakeProactiveDao, currentHourProvider = { 12 })
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }

        anomalyDetector = AnomalyDetector(
            context = fakeContext,
            signalsDao = fakeSignalsDao,
            nudgePolicy = nudgePolicy
        )
    }

    @Test
    fun testAnomalyDetector_restingHrSpike_triggersAlert() = runBlocking {
        val today = LocalDate.now()

        // 1. Fill 10 days of baseline resting HR around 60 bpm
        for (i in 1..10) {
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = today.minusDays(i.toLong()).toString(),
                    restingHr = 60 + (i % 3) // 60, 61, 62
                )
            )
        }

        // 2. Today's resting HR spikes to 115 bpm (> 2 stddev above mean)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = today.toString(),
                restingHr = 115
            )
        )

        // 3. Detect anomalies
        anomalyDetector.detectAndNotify()

        // 4. Verify an anomaly nudge was recorded in ProactiveDao
        val lastNudge = fakeProactiveDao.getLastNudgeOfType("anomaly_hr")
        assertTrue(lastNudge != null)
        assertEquals("anomaly_hr", lastNudge?.type)
    }

    @Test
    fun testAnomalyDetector_normalHr_doesNotTriggerAlert() = runBlocking {
        val today = LocalDate.now()

        // Baseline around 60 bpm
        for (i in 1..10) {
            fakeSignalsDao.insertOrUpdate(
                DailySignalsEntity(
                    date = today.minusDays(i.toLong()).toString(),
                    restingHr = 60 + (i % 3)
                )
            )
        }

        // Today's resting HR is normal (62 bpm)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = today.toString(),
                restingHr = 62
            )
        )

        anomalyDetector.detectAndNotify()

        // Verify NO anomaly nudge was recorded
        val lastNudge = fakeProactiveDao.getLastNudgeOfType("anomaly_hr")
        assertTrue(lastNudge == null)
    }
}
