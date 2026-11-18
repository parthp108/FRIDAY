package com.health.friday.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRouterLogicTest {

    @Test
    fun testExecutionRequestCreation() {
        val request = ExecutionRequest(
            actionName = "CLICK",
            packageName = "com.health.friday",
            queryParams = mapOf("deeplink" to "friday://home"),
            textPayload = "test",
            targetElementId = 1
        )

        assertEquals("CLICK", request.actionName)
        assertEquals("com.health.friday", request.packageName)
        assertEquals("friday://home", request.queryParams["deeplink"])
        assertEquals("test", request.textPayload)
        assertEquals(1, request.targetElementId)
    }

    @Test
    fun testExecutionResultCreation() {
        val result = ExecutionResult(
            success = true,
            tierUsed = ExecutionTier.TIER_3_DEEP_LINK,
            message = "Success"
        )

        assertTrue(result.success)
        assertEquals(ExecutionTier.TIER_3_DEEP_LINK, result.tierUsed)
        assertEquals("Success", result.message)
    }
}
