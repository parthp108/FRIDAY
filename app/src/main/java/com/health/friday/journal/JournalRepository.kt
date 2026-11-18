package com.health.friday.journal

import com.health.friday.data.local.JournalDao
import com.health.friday.data.local.JournalEntry
import kotlinx.coroutines.flow.Flow

import com.health.friday.memory.rag.FridayEmbedder
import org.json.JSONArray

class JournalRepository(
    private val journalDao: JournalDao,
    private val embedder: FridayEmbedder
) {

    fun getEntries(): Flow<List<JournalEntry>> {
        return journalDao.getAll()
    }

    // Returns the saved entry, or null if the text was blank.
    suspend fun saveEntry(
        text: String
    ): JournalEntry? {

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            return null
        }

        embedder.initialize()
        val embedding = embedder.embed(cleanText)
        val embeddingJson = embedding?.let {
            val array = JSONArray()
            it.forEach { floatVal -> array.put(floatVal.toDouble()) }
            array.toString()
        }

        val entry = JournalEntry(text = cleanText, embeddingJson = embeddingJson)

        val id = journalDao.insert(entry)

        return entry.copy(id = id)
    }

    // Not used yet: the "Send with feedback" flow will call this
    // once the AI client exists.
    suspend fun attachFeedback(
        entry: JournalEntry,
        feedback: String
    ) {
        journalDao.update(
            entry.copy(feedback = feedback)
        )
    }

    suspend fun deleteEntry(
        entry: JournalEntry
    ) {
        journalDao.delete(entry)
    }
}