package com.health.friday.memory.graph

import com.health.friday.memory.rag.FridayEmbedder
import com.health.friday.memory.rag.VectorMath
import kotlin.math.exp

class MemoryRetriever(
    private val embedder: FridayEmbedder
) {

    fun scoreAndRank(
        nodes: List<MemoryNode>,
        query: String,
        maxResults: Int = 5,
        lambda: Double = 0.001 // Time decay constant per hour
    ): List<Pair<MemoryNode, Float>> {
        if (nodes.isEmpty()) return emptyList()

        val queryEmbedding = embedder.embed(query)
        val currentTime = System.currentTimeMillis()
        val queryLower = query.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 }.toSet()

        val scored = nodes.mapNotNull { node ->
            if (node.isSuperseded) return@mapNotNull null

            // 1. Cosine similarity
            val cosineSim = if (queryEmbedding != null && node.embedding.isNotEmpty()) {
                VectorMath.cosineSimilarity(queryEmbedding, node.embedding)
            } else 0f

            // 2. Keyword/BM25 overlap
            val nodeTextLower = node.rawText.lowercase()
            val matchCount = queryLower.count { word -> nodeTextLower.contains(word) }
            val keywordScore = if (queryLower.isNotEmpty()) (matchCount.toFloat() / queryLower.size.toFloat()) else 0f

            // 3. Exponential Time Decay
            val hoursElapsed = (currentTime - node.timestamp) / (1000.0 * 60.0 * 60.0)
            val timeDecay = exp(-lambda * hoursElapsed).toFloat()

            // Composite Retrieval Score
            val totalScore = (0.5f * cosineSim) + (0.2f * keywordScore) + (0.15f * node.importanceScore) + (0.15f * timeDecay)

            Pair(node, totalScore)
        }

        return scored.sortedByDescending { it.second }.take(maxResults)
    }
}
