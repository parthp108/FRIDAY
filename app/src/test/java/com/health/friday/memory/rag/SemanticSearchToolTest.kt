package com.health.friday.memory.rag

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.data.local.JournalDao
import com.health.friday.data.local.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeJournalDao : JournalDao {
    val entries = mutableListOf<JournalEntry>()
    private var idCounter = 1L

    override suspend fun getBySyncId(syncId: String): JournalEntry? = entries.find { it.syncId == syncId }

    override suspend fun deleteBySyncId(syncId: String): Int {
        val count = entries.count { it.syncId == syncId }
        entries.removeAll { it.syncId == syncId }
        return count
    }

    override suspend fun insert(entry: JournalEntry): Long {
        val inserted = entry.copy(id = idCounter++)
        entries.add(inserted)
        return inserted.id
    }

    override suspend fun update(entry: JournalEntry) {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) entries[index] = entry
    }

    override suspend fun delete(entry: JournalEntry) {
        entries.removeAll { it.id == entry.id }
    }

    override fun getAll(): Flow<List<JournalEntry>> = flowOf(entries)

    override suspend fun getAllNow(): List<JournalEntry> = entries
}

class TestableEmbedder(context: Context) : FridayEmbedder(context) {
    var embeddingToReturn: FloatArray? = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)

    override suspend fun initialize() {
        // No-op for unit test
    }

    override fun embed(text: String): FloatArray? {
        return embeddingToReturn
    }
}

class SemanticSearchToolTest {

    private lateinit var fakeDao: FakeJournalDao
    private lateinit var fakeContext: Context
    private lateinit var fakeEmbedder: TestableEmbedder
    private lateinit var tool: SemanticSearchTool

    @Before
    fun setUp() {
        fakeDao = FakeJournalDao()
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        fakeEmbedder = TestableEmbedder(fakeContext)
        tool = SemanticSearchTool(fakeDao, fakeEmbedder)
    }

    @Test
    fun testSemanticSearchTool_emptyJournal_returnsNoEntries() = runBlocking {
        val result = tool.execute(mapOf("query" to "feeling burned out"))
        assertEquals("No journal entries found.", result)
    }

    @Test
    fun testSemanticSearchTool_vectorMatch_findsExhaustedEntry() = runBlocking {
        // Insert entry with vector embedding
        val floatArr = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val embeddingJson = JSONArray().apply {
            floatArr.forEach { put(it.toDouble()) }
        }.toString()

        fakeDao.insert(
            JournalEntry(
                text = "Man, I feel so drained today. Work was exhausting and I just want to crash.",
                embeddingJson = embeddingJson
            )
        )

        fakeEmbedder.embeddingToReturn = floatArr

        val result = tool.execute(mapOf("query" to "feeling burned out"))

        assertTrue(result.contains("Top Journal Matches"))
        assertTrue(result.contains("drained today"))
    }
}
