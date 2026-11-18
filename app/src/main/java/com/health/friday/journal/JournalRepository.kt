package com.health.friday.journal

import com.health.friday.data.local.JournalDao
import com.health.friday.data.local.JournalEntry
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncMutation
import com.health.friday.sync.cloud.toCloudSyncPayload
import kotlinx.coroutines.flow.Flow
import com.health.friday.memory.rag.FridayEmbedder
import org.json.JSONArray

class JournalRepository(
    private val journalDao: JournalDao,
    private val embedder: FridayEmbedder,
    private val cloudSyncWriter: AndroidCloudSyncWriter? = null
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

        val writer = cloudSyncWriter

        return if (writer == null) {
            val id = journalDao.insert(entry)
            entry.copy(id = id)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.JOURNAL_ENTRY,
                entityId = entry.syncId
            ) { hlc ->
                val stamped = entry.copy(syncHlc = hlc)
                val id = journalDao.insert(stamped)

                CloudSyncMutation(
                    value = stamped.copy(id = id),
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    // Not used yet: the "Send with feedback" flow will call this
    // once the AI client exists.
    suspend fun attachFeedback(
        entry: JournalEntry,
        feedback: String
    ) {
        val updated = entry.copy(feedback = feedback)
        val writer = cloudSyncWriter

        if (writer == null) {
            journalDao.update(updated)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.JOURNAL_ENTRY,
                entityId = entry.syncId
            ) { hlc ->
                val stamped = updated.copy(syncHlc = hlc)
                journalDao.update(stamped)

                CloudSyncMutation(
                    value = Unit,
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    suspend fun deleteEntry(
        entry: JournalEntry
    ) {
        val writer = cloudSyncWriter

        if (writer == null) {
            journalDao.delete(entry)
        } else {
            writer.delete(
                entityType = CloudSyncEntityTypes.JOURNAL_ENTRY,
                entityId = entry.syncId
            ) {
                journalDao.delete(entry)
            }
        }
    }
}