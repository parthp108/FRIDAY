package com.health.friday.journal

import com.health.friday.data.local.JournalDao
import com.health.friday.data.local.JournalEntry
import kotlinx.coroutines.flow.Flow

class JournalRepository(
    private val journalDao: JournalDao
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

        val entry = JournalEntry(text = cleanText)

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