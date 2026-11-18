package com.health.friday.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatEngineTest {

    @Test
    fun testMean() {
        assertEquals(0.0, StatEngine.mean(emptyList()), 0.001)
        assertEquals(5.0, StatEngine.mean(listOf(2.0, 4.0, 6.0, 8.0)), 0.001)
    }

    @Test
    fun testStdDev() {
        assertEquals(0.0, StatEngine.stdDev(listOf(5.0)), 0.001)
        // Values: 2, 4, 4, 4, 5, 5, 7, 9. Sample StdDev = 2.138
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        assertEquals(2.138, StatEngine.stdDev(values), 0.01)
    }

    @Test
    fun testPearson() {
        // Less than 3 points
        assertNull(StatEngine.pearson(listOf(1.0, 2.0), listOf(3.0, 4.0)))

        // Perfect positive correlation: y = 2x
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0)
        val rPos = StatEngine.pearson(x, y)
        assertNotNull(rPos)
        assertEquals(1.0, rPos!!, 0.001)

        // Perfect negative correlation: y = -2x
        val yNeg = listOf(10.0, 8.0, 6.0, 4.0, 2.0)
        val rNeg = StatEngine.pearson(x, yNeg)
        assertNotNull(rNeg)
        assertEquals(-1.0, rNeg!!, 0.001)
    }

    @Test
    fun testSpearman() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val rho = StatEngine.spearman(x, y)
        assertNotNull(rho)
        assertEquals(1.0, rho!!, 0.001)
    }

    @Test
    fun testLinearRegression() {
        // y = 2x + 1
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = listOf(3.0, 5.0, 7.0, 9.0, 11.0)
        val line = StatEngine.linearRegression(x, y)
        assertNotNull(line)
        val (slope, intercept) = line!!
        assertEquals(2.0, slope, 0.001)
        assertEquals(1.0, intercept, 0.001)
    }

    @Test
    fun testInterpretCorrelation() {
        assertEquals("Strong positive correlation", StatEngine.interpretCorrelation(0.85))
        assertEquals("Moderate negative correlation", StatEngine.interpretCorrelation(-0.55))
        assertEquals("Weak positive correlation", StatEngine.interpretCorrelation(0.25))
        assertEquals("No meaningful correlation", StatEngine.interpretCorrelation(0.05))
    }
}
