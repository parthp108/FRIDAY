package com.health.friday.memory

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.memory.graph.GraphManager

class MemoryTool(
    private val dao: MemoryFactDao,
    private val graphManager: GraphManager? = null
) : AiTool {
    override val name = "manage_memory"
    override val description = "Saves or deletes permanent facts about the user (e.g., preferences, allergies, goals). Use to remember important details across sessions."
    override val parameters = listOf(
        AiToolParameter("action", "Either 'save' or 'delete'", required = true),
        AiToolParameter("fact", "The exact fact to save or delete", required = true)
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val action = arguments["action"]?.trim()?.lowercase() ?: return "Missing action"
        val factStr = arguments["fact"]?.trim() ?: return "Missing fact"

        val facts = dao.getAllFacts()

        if (action == "save") {
            if (facts.any { it.fact.equals(factStr, ignoreCase = true) }) {
                return "Fact already remembered."
            }
            if (graphManager != null) {
                graphManager.insertOrUpdateMemory(factStr)
            } else {
                dao.insertFact(MemoryFactEntity(fact = factStr))
            }
            return "Remembered in Unified Memory Graph: $factStr"
        } else if (action == "delete") {
            val toDelete = facts.firstOrNull { it.fact.contains(factStr, ignoreCase = true) }
            if (toDelete != null) {
                dao.deleteFact(toDelete)
                return "Forgot: ${toDelete.fact}"
            }
            return "Fact not found to delete."
        }
        return "Unknown action. Use 'save' or 'delete'."
    }
}
