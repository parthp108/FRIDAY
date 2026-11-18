package com.health.friday.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NarrationGuardTest {

    private lateinit var guard: NarrationGuard

    @Before
    fun setUp() {
        guard = NarrationGuard()
    }

    @Test
    fun testGuardResponse_claimsCorrelationWithoutTool_blocksHallucination() {
        val reply = "There is a strong correlation between your sleep and steps."
        val conversation = listOf(
            AiMessage(role = "user", content = "Does sleep correlate with steps?"),
            AiMessage(role = "assistant", content = reply)
        )

        val guarded = guard.guardResponse(reply, conversation)

        // Must refuse hallucinated correlation claim when no correlation tool was executed
        assertTrue(guarded.contains("I cannot make statistical claims without running the numbers"))
    }

    @Test
    fun testGuardResponse_modelClaimsStrongWhenToolSaysWeak_overwritesHallucination() {
        val reply = "Based on the math, there is a strong correlation between eating late and poor sleep."
        val conversation = listOf(
            AiMessage(role = "user", content = "Does eating late correlate with sleep?"),
            AiMessage(
                role = "tool",
                content = "Analyzed 14 days. Correlation between last_meal_hour vs sleep: Pearson r = 0.25 (Weak positive correlation)"
            ),
            AiMessage(role = "assistant", content = reply)
        )

        val guarded = guard.guardResponse(reply, conversation)

        // Must correct "strong" to "weak" based on tool output
        assertTrue(guarded.contains("Mathematical correction"))
        assertTrue(guarded.contains("weak"))
    }

    @Test
    fun testGuardResponse_truthfulReply_passesThrough() {
        val reply = "Here is your plan for today: hit 10,000 steps and drink 2L water."
        val conversation = listOf(
            AiMessage(role = "user", content = "What should I do today?"),
            AiMessage(role = "assistant", content = reply)
        )

        val guarded = guard.guardResponse(reply, conversation)

        // Pass through unchanged
        assertEquals(reply, guarded)
    }
}
