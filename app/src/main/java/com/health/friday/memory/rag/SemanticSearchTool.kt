package com.health.friday.memory.rag

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.data.local.JournalDao
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SemanticSearchTool(
    private val journalDao: JournalDao,
    private val embedder: FridayEmbedder
) : AiTool {
    override val name = "search_journal"
    override val description = "Searches the user's past journal entries by semantic meaning (feelings, events, context). Returns the closest matches."
    override val parameters = listOf(
        AiToolParameter("query", "The search query (e.g. 'feeling burned out', 'slept badly', 'productive day').", required = true)
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val query = arguments["query"]?.trim() ?: return "Missing query"

        val entries = journalDao.getAllNow()
        if (entries.isEmpty()) return "No journal entries found."

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

        // 1. Try vector embedding search if embedder is initialized
        try {
            embedder.initialize()
            val queryEmbedding = embedder.embed(query)
            if (queryEmbedding != null) {
                val scoredEntries = entries.mapNotNull { entry ->
                    val embJson = entry.embeddingJson
                    if (embJson.isNullOrBlank()) return@mapNotNull null
                    try {
                        val arr = JSONArray(embJson)
                        val floatArr = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
                        val score = VectorMath.cosineSimilarity(queryEmbedding, floatArr)
                        Triple(entry, score, floatArr)
                    } catch (_: Exception) { null }
                }.filter { it.second > 0.35f }

                if (scoredEntries.isNotEmpty()) {
                    val topMatches = scoredEntries.sortedByDescending { it.second }.take(3)
                    return buildString {
                        append("Top Journal Matches for '$query':\n")
                        topMatches.forEach { (entry, score, _) ->
                            val date = dateFormat.format(Date(entry.createdAt))
                            append("- [$date] (Match: ${(score * 100).toInt()}%): ${entry.text}\n")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to keyword search if vector embedder fails
        }

        // 2. Keyword / Stem Text Matching Fallback
        val stopWords = setOf("did", "mention", "recently", "rectly", "about", "that", "this", "what", "have", "with", "from", "some", "you")
        val queryWords = query.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }

        val stems = queryWords.map { if (it.length > 4) it.take(4) else it }.toSet()

        val matchedEntries = if (stems.isEmpty()) {
            entries.take(3)
        } else {
            entries.mapNotNull { entry ->
                val textLower = entry.text.lowercase()
                val score = stems.count { stem -> textLower.contains(stem) }
                if (score > 0) Pair(entry, score) else null
            }.sortedByDescending { it.second }.map { it.first }.take(3)
        }

        if (matchedEntries.isEmpty()) {
            return "No matching journal entries found for '$query'."
        }

        return buildString {
            append("Journal Entries found matching '$query':\n")
            matchedEntries.forEach { entry ->
                val date = dateFormat.format(Date(entry.createdAt))
                append("- [$date]: ${entry.text}\n")
            }
        }
    }
}
