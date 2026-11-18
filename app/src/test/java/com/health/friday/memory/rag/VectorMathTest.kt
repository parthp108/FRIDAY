package com.health.friday.memory.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test
    fun testCosineSimilarity_identicalVectors_returnsOne() {
        val v1 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        val v2 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)

        val sim = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarity_orthogonalVectors_returnsZero() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)

        val sim = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarity_oppositeVectors_returnsNegativeOne() {
        val v1 = floatArrayOf(1.0f, 2.0f)
        val v2 = floatArrayOf(-1.0f, -2.0f)

        val sim = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(-1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarity_differentSizes_returnsZero() {
        val v1 = floatArrayOf(1.0f, 2.0f)
        val v2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        val sim = VectorMath.cosineSimilarity(v1, v2)
        assertEquals(0.0f, sim, 0.001f)
    }
}
