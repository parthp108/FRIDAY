package com.health.friday.agentic

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSystemController(context: Context) : SystemController(context) {
    var dndEnabledState = false
    var dndAccessGranted = true
    var timerMinutesSet = 0
    var timerMessageSet = ""

    override fun hasDndAccess(): Boolean = dndAccessGranted

    override fun toggleDoNotDisturb(enable: Boolean): Boolean {
        if (!dndAccessGranted) return false
        dndEnabledState = enable
        return true
    }

    override fun setTimer(minutes: Int, message: String): Boolean {
        timerMinutesSet = minutes
        timerMessageSet = message
        return true
    }
}

class FocusModeToolTest {

    private lateinit var fakeContext: Context
    private lateinit var fakeSystemController: FakeSystemController
    private lateinit var tool: FocusModeTool

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        fakeSystemController = FakeSystemController(fakeContext)
        tool = FocusModeTool(fakeSystemController)
    }

    @Test
    fun testFocusModeTool_enableFocusModeWithTimer() = runBlocking {
        val args = mapOf(
            "state" to "on",
            "duration_minutes" to "30",
            "intent" to "reading a book"
        )

        val result = tool.execute(args)

        assertTrue(fakeSystemController.dndEnabledState)
        assertEquals(30, fakeSystemController.timerMinutesSet)
        assertEquals("FRIDAY: reading a book Complete", fakeSystemController.timerMessageSet)

        assertTrue(result.contains("Focus Mode initiated for 30 minutes (reading a book)"))
        assertTrue(result.contains("Do Not Disturb: ENABLED"))
        assertTrue(result.contains("Timer set for 30 minutes"))
    }

    @Test
    fun testFocusModeTool_disableFocusMode() = runBlocking {
        fakeSystemController.dndEnabledState = true

        val args = mapOf("state" to "off")
        val result = tool.execute(args)

        // DND should be disabled
        assertTrue(!fakeSystemController.dndEnabledState)
        assertTrue(result.contains("Focus Mode ended. Do Not Disturb: DISABLED"))
    }
}
