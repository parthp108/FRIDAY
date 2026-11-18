package com.health.friday.agentic

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothControlToolTest {

    private val fakeContext: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
    private val tool = BluetoothControlTool(fakeContext)

    @Test
    fun declaresRequiredStateParameter() {
        assertTrue(tool.parameters.any { it.name == "state" && it.required })
    }

    @Test
    fun keepsToolName() {
        assertEquals("control_bluetooth", tool.name)
    }
}
