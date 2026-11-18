package com.health.friday.journal

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.memory.rag.FridayEmbedder
import com.health.friday.memory.rag.FakeJournalDao
import com.health.friday.memory.rag.TestableEmbedder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JournalRepositoryTest {

    private lateinit var fakeDao: FakeJournalDao
    private lateinit var fakeContext: Context
    private lateinit var fakeEmbedder: TestableEmbedder
    private lateinit var repository: JournalRepository

    @Before
    fun setUp() {
        fakeDao = FakeJournalDao()
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        fakeEmbedder = TestableEmbedder(fakeContext)
        repository = JournalRepository(fakeDao, fakeEmbedder)
    }

    @Test
    fun testSaveEntry_serializesEmbeddingToJsonArray() = runBlocking {
        fakeEmbedder.embeddingToReturn = floatArrayOf(0.5f, -0.25f, 0.75f)

        val entry = repository.saveEntry("Man, I feel so drained today. Work was exhausting and I just want to crash.")

        assertNotNull(entry)
        assertEquals("Man, I feel so drained today. Work was exhausting and I just want to crash.", entry?.text)
        assertNotNull(entry?.embeddingJson)
        val json = entry?.embeddingJson ?: ""
        assertTrue(json.contains("0.5"))
        assertTrue(json.contains("-0.25"))
        assertTrue(json.contains("0.75"))
    }
}
