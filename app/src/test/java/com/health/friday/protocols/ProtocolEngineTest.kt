package com.health.friday.protocols

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.ai.AiToolRegistry
import com.health.friday.data.local.DailySignalsEntity
import com.health.friday.proactive.FakeDailySignalsDao
import com.health.friday.proactive.FakeProactiveDao
import com.health.friday.proactive.NudgePolicy
import com.health.friday.proactive.ProtocolEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ProtocolEngineTest {

    private lateinit var fakeProactiveDao: FakeProactiveDao
    private lateinit var fakeSignalsDao: FakeDailySignalsDao
    private lateinit var fakeContext: Context
    private lateinit var engine: ProtocolEngine

    @Before
    fun setUp() {
        fakeProactiveDao = FakeProactiveDao()
        fakeSignalsDao = FakeDailySignalsDao()
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }

        engine = ProtocolEngine(
            context = fakeContext,
            proactiveDao = fakeProactiveDao,
            signalsDao = fakeSignalsDao,
            toolRegistry = AiToolRegistry(tools = emptyList())
        )
    }

    @Test
    fun testSeedDefaultsIfEmpty() = runBlocking {
        assertEquals(0, fakeProactiveDao.getProtocolCount())

        engine.seedDefaultsIfEmpty()

        assertEquals(1, fakeProactiveDao.getProtocolCount())
        val protocols = fakeProactiveDao.getActiveProtocols()
        assertEquals(1, protocols.size)
        assertEquals("Late Night Lock Down", protocols[0].title)
    }

    @Test
    fun testEvaluateAndTrigger_whenConditionMet_firesProtocol() = runBlocking {
        val todayStr = LocalDate.now().toString()

        // 1. Insert a protocol rule: "kcal < 1000"
        val protocolId = fakeProactiveDao.insertProtocol(
            ProtocolEntity(
                id = 10,
                title = "Low Kcal Alert",
                triggerConditionJson = """{"metric":"kcal","operator":"<","value":1000}""",
                actionTool = "some_tool",
                actionPayloadJson = "{}",
                autonomyLevel = "ASK_FIRST",
                isEnabled = true
            )
        )

        // 2. Set today's signal to kcal = 500 (< 1000)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = todayStr,
                kcal = 500
            )
        )

        // 3. Evaluate and trigger
        engine.evaluateAndTrigger()

        // 4. Verify a nudge record with type "protocol:10" was created
        val lastNudge = fakeProactiveDao.getLastNudgeOfType(NudgePolicy.PROTOCOL_PREFIX + protocolId)
        assertTrue(lastNudge != null)
        assertFalse(lastNudge!!.interacted)
    }

    @Test
    fun testEvaluateAndTrigger_whenConditionNotMet_doesNotFire() = runBlocking {
        val todayStr = LocalDate.now().toString()

        val protocolId = fakeProactiveDao.insertProtocol(
            ProtocolEntity(
                id = 20,
                title = "Low Kcal Alert",
                triggerConditionJson = """{"metric":"kcal","operator":"<","value":1000}""",
                actionTool = "some_tool",
                actionPayloadJson = "{}",
                autonomyLevel = "ASK_FIRST",
                isEnabled = true
            )
        )

        // Set today's signal to kcal = 1800 (NOT < 1000)
        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = todayStr,
                kcal = 1800
            )
        )

        engine.evaluateAndTrigger()

        // Verify NO nudge record was created for this protocol
        val lastNudge = fakeProactiveDao.getLastNudgeOfType(NudgePolicy.PROTOCOL_PREFIX + protocolId)
        assertTrue(lastNudge == null)
    }

    @Test
    fun testEvaluateAndTrigger_throttles_doesNotFireTwiceInSameDay() = runBlocking {
        val todayStr = LocalDate.now().toString()

        val protocolId = fakeProactiveDao.insertProtocol(
            ProtocolEntity(
                id = 30,
                title = "Low Kcal Alert",
                triggerConditionJson = """{"metric":"kcal","operator":"<","value":1000}""",
                actionTool = "some_tool",
                actionPayloadJson = "{}",
                autonomyLevel = "ASK_FIRST",
                isEnabled = true
            )
        )

        fakeSignalsDao.insertOrUpdate(
            DailySignalsEntity(
                date = todayStr,
                kcal = 200
            )
        )

        // First run fires
        engine.evaluateAndTrigger()
        val nudgesCountAfterFirstRun = fakeProactiveDao.nudges.size
        assertEquals(1, nudgesCountAfterFirstRun)

        // Second run on same day should be throttled (alreadyFiredToday)
        engine.evaluateAndTrigger()
        val nudgesCountAfterSecondRun = fakeProactiveDao.nudges.size
        assertEquals(1, nudgesCountAfterSecondRun)
    }
}
