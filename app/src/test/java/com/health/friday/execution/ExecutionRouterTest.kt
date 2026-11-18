package com.health.friday.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionRouterTest {
    @Test
    fun testTiersCount() {
        assertEquals(4, ExecutionTier.entries.size)
    }
}
