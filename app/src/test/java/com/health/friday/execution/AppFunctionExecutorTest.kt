package com.health.friday.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFunctionExecutorTest {
    @Test
    fun testClassExists() {
        assertEquals(ExecutionTier.TIER_1_APP_FUNCTIONS.name, "TIER_1_APP_FUNCTIONS")
    }
}
