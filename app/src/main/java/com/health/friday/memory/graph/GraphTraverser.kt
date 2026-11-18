package com.health.friday.memory.graph

import android.util.Log

class GraphTraverser(
    private val relationDao: MemoryRelationDao
) {

    companion object {
        private const val TAG = "GraphTraverser"
    }

    /**
     * Discovers multi-hop connected fact IDs starting from a set of target seed fact IDs.
     * Uses BFS graph traversal up to maxHops.
     */
    suspend fun traverseConnectedFactIds(
        seedFactIds: List<Long>,
        maxHops: Int = 2
    ): Map<Long, String> {
        val visitedFactIds = mutableSetOf<Long>()
        val relationMap = mutableMapOf<Long, String>() // factId -> relation predicate description

        var currentLevel = seedFactIds.toSet()
        visitedFactIds.addAll(seedFactIds)

        var hop = 0
        while (currentLevel.isNotEmpty() && hop < maxHops) {
            val nextLevel = mutableSetOf<Long>()

            for (factId in currentLevel) {
                val relations = runCatching { relationDao.getAllRelationsForEntity(factId) }.getOrDefault(emptyList())
                for (rel in relations) {
                    val neighborId = if (rel.subjectFactId == factId) rel.objectFactId else rel.subjectFactId
                    if (!visitedFactIds.contains(neighborId)) {
                        visitedFactIds.add(neighborId)
                        nextLevel.add(neighborId)
                        relationMap[neighborId] = rel.predicate
                    }
                }
            }

            currentLevel = nextLevel
            hop++
        }

        Log.d(TAG, "Graph traversal from seeds $seedFactIds discovered ${relationMap.size} connected nodes")
        return relationMap
    }
}
