package com.health.friday.memory.graph

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMemoryRelationDao : MemoryRelationDao {
    private val relations = mutableListOf<MemoryRelationEntity>()

    override suspend fun insertRelation(relation: MemoryRelationEntity): Long {
        val newId = (relations.size + 1).toLong()
        val saved = relation.copy(id = newId)
        relations.add(saved)
        return newId
    }

    override suspend fun getRelationsForSubject(subjectId: Long): List<MemoryRelationEntity> {
        return relations.filter { it.subjectFactId == subjectId }
    }

    override suspend fun getRelationsForObject(objectId: Long): List<MemoryRelationEntity> {
        return relations.filter { it.objectFactId == objectId }
    }

    override suspend fun getAllRelationsForEntity(subjectId: Long): List<MemoryRelationEntity> {
        return relations.filter { it.subjectFactId == subjectId || it.objectFactId == subjectId }
    }

    override suspend fun getAllRelations(): List<MemoryRelationEntity> {
        return relations.toList()
    }

    override suspend fun deleteRelationsForFact(factId: Long) {
        relations.removeAll { it.subjectFactId == factId || it.objectFactId == factId }
    }
}

class GraphTraverserTest {

    private lateinit var fakeDao: FakeMemoryRelationDao
    private lateinit var traverser: GraphTraverser

    @Before
    fun setUp() {
        fakeDao = FakeMemoryRelationDao()
        traverser = GraphTraverser(fakeDao)
    }

    @Test
    fun testMultiHopGraphTraversal() = runBlocking {
        // Fact 1 -> supersedes -> Fact 2
        fakeDao.insertRelation(
            MemoryRelationEntity(id = 1, subjectFactId = 10L, predicate = "supersedes", objectFactId = 5L)
        )
        // Fact 2 -> prefers -> Fact 3
        fakeDao.insertRelation(
            MemoryRelationEntity(id = 2, subjectFactId = 5L, predicate = "prefers", objectFactId = 20L)
        )

        // Traversing 2-hop graph from seed Fact 10
        val discovered = traverser.traverseConnectedFactIds(listOf(10L), maxHops = 2)

        assertTrue(discovered.containsKey(5L))
        assertTrue(discovered.containsKey(20L))
        assertEquals("supersedes", discovered[5L])
        assertEquals("prefers", discovered[20L])
    }
}
