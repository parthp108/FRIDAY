package com.health.friday.memory.graph

import com.health.friday.ai.AiClient
import com.health.friday.ai.AiContext
import com.health.friday.ai.AiMessage
import com.health.friday.memory.MemoryFactDao
import com.health.friday.memory.MemoryFactEntity
import com.health.friday.memory.rag.FridayEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

class GraphManager(
    private val factDao: MemoryFactDao,
    private val embedder: FridayEmbedder,
    private val retriever: MemoryRetriever,
    private val aiClientProvider: (() -> AiClient?)? = null
) {
    private val memoryNodes = CopyOnWriteArrayList<MemoryNode>()
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        embedder.initialize()
        val facts = factDao.getAllFacts()
        memoryNodes.clear()
        facts.forEach { factEntity ->
            val embedding = embedder.embed(factEntity.fact) ?: FloatArray(0)
            memoryNodes.add(
                MemoryNode(
                    id = factEntity.id,
                    timestamp = factEntity.createdAt,
                    rawText = factEntity.fact,
                    embedding = embedding
                )
            )
        }
        isInitialized = true
    }

    suspend fun insertOrUpdateMemory(rawText: String, sourceApp: String = "Voice"): MemoryNode = withContext(Dispatchers.IO) {
        initialize()
        val newEmbedding = embedder.embed(rawText) ?: FloatArray(0)

        // 1. Search top 3 nearest neighbor memories
        val topNeighbors = retriever.scoreAndRank(memoryNodes.toList(), rawText, maxResults = 3)

        var supersededNode: MemoryNode? = null

        // 2. Contradiction & Supersede Check
        for ((neighbor, score) in topNeighbors) {
            if (score > 0.45f) {
                val prompt = """
                    Analyze these two user facts or preferences:
                    Old Fact: "${neighbor.rawText}"
                    New Fact: "$rawText"
                    Does the New Fact directly contradict or update/supersede the Old Fact?
                    Answer strictly "YES" or "NO".
                """.trimIndent()

                val response = runCatching {
                    aiClientProvider?.invoke()?.sendMessage(
                        listOf(AiMessage(role = "user", content = prompt)),
                        AiContext()
                    )?.message
                }.getOrNull()

                if (response?.contains("YES", ignoreCase = true) == true) {
                    neighbor.isSuperseded = true
                    supersededNode = neighbor
                    break
                }
            }
        }

        // 3. Insert new Memory Fact into DAO & Graph
        val id = factDao.insertFact(MemoryFactEntity(fact = rawText))
        val newNode = MemoryNode(
            id = id,
            timestamp = System.currentTimeMillis(),
            sourceApp = sourceApp,
            rawText = rawText,
            embedding = newEmbedding,
            supersededById = supersededNode?.id
        )

        memoryNodes.add(newNode)
        return@withContext newNode
    }

    suspend fun queryGraphMemory(query: String, maxResults: Int = 5): String = withContext(Dispatchers.IO) {
        initialize()
        val ranked = retriever.scoreAndRank(memoryNodes.toList(), query, maxResults = maxResults)
        if (ranked.isEmpty()) return@withContext "No relevant memory graph nodes found."

        return@withContext buildString {
            append("Relevant Associative Memories for '$query':\n")
            ranked.forEach { (node, score) ->
                val scorePercent = (score * 100).toInt()
                append("- [Match: $scorePercent%] ${node.rawText}\n")
            }
        }
    }

    fun getAllActiveNodes(): List<MemoryNode> {
        return memoryNodes.filter { !it.isSuperseded }
    }
}
