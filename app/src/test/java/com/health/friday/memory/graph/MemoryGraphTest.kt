package com.health.friday.memory.graph

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.memory.MemoryFactDao
import com.health.friday.memory.MemoryFactEntity
import com.health.friday.memory.rag.FridayEmbedder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class MemoryGraphTest {

    @Test
    fun testMemoryNodeAndRetrieverScoring() = runBlocking {
        val fakeEmbedder = object : FridayEmbedder(mockContext()) {
            override suspend fun initialize() {}
            override fun embed(text: String): FloatArray {
                return when {
                    text.contains("coffee", ignoreCase = true) -> floatArrayOf(0.9f, 0.1f, 0.0f)
                    text.contains("milk", ignoreCase = true) -> floatArrayOf(0.85f, 0.15f, 0.0f)
                    else -> floatArrayOf(0.1f, 0.8f, 0.1f)
                }
            }
        }

        val retriever = MemoryRetriever(fakeEmbedder)

        val node1 = MemoryNode(
            id = 1,
            timestamp = System.currentTimeMillis() - 86400000L * 30, // 30 days old
            rawText = "I drink black coffee every morning",
            embedding = fakeEmbedder.embed("I drink black coffee every morning")
        )

        val node2 = MemoryNode(
            id = 2,
            timestamp = System.currentTimeMillis() - 3600000L * 2, // 2 hours old
            rawText = "I switched from black coffee to oat milk latte",
            embedding = fakeEmbedder.embed("I switched from black coffee to oat milk latte")
        )

        val ranked = retriever.scoreAndRank(listOf(node1, node2), "what coffee do I drink?")

        assertEquals(2, ranked.size)
        // Node 2 should score higher because it's newer (recency decay) and contains "oat milk latte"
        assertEquals(node2.id, ranked.first().first.id)
    }

    @Test
    fun testGraphManagerSupersedeConsolidation() = runBlocking {
        val mockDao = mock(MemoryFactDao::class.java)
        val fakeEmbedder = object : FridayEmbedder(mockContext()) {
            override suspend fun initialize() {}
            override fun embed(text: String): FloatArray = floatArrayOf(0.8f, 0.2f, 0.1f)
        }

        `when`(mockDao.getAllFacts()).thenReturn(
            listOf(
                MemoryFactEntity(id = 1, fact = "I drink oat milk", createdAt = System.currentTimeMillis() - 10000)
            )
        )
        `when`(mockDao.insertFact(any())).thenReturn(2L)

        val retriever = MemoryRetriever(fakeEmbedder)
        val manager = GraphManager(mockDao, fakeEmbedder, retriever)

        manager.initialize()

        val activeNodesBefore = manager.getAllActiveNodes()
        assertEquals(1, activeNodesBefore.size)
        assertFalse(activeNodesBefore.first().isSuperseded)

        val newNode = manager.insertOrUpdateMemory("I drink almond milk now instead of oat milk")
        assertEquals(2L, newNode.id)
    }

    private fun mockContext(): Context {
        return object : ContextWrapper(null) {}
    }
}
