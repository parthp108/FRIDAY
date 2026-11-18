package com.health.friday.agentic

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SystemControllerTest {

    private lateinit var fakeContext: Context
    private lateinit var systemController: SystemController

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        systemController = SystemController(fakeContext)
    }

    @Test
    fun testSystemController_initialization() {
        assertNotNull(systemController)
    }
}
