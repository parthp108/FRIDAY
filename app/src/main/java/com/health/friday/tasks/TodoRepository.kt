
package com.health.friday.tasks

import com.health.friday.data.local.TodoDao
import com.health.friday.data.local.TodoItem
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncMutation
import com.health.friday.sync.cloud.toCloudSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TodoRepository(
    private val todoDao: TodoDao,
    private val cloudSyncWriter: AndroidCloudSyncWriter? = null
) {

    fun getTodos(): Flow<List<TodoItem>> {
        return todoDao.getAll()
    }

    suspend fun getTodosNow(): List<TodoItem> {
        return todoDao.getAll().first()
    }

    // Returns the saved item, or null if the title was blank.
    suspend fun addTodo(
        title: String
    ): TodoItem? {
        val cleanTitle = title.trim()

        if (cleanTitle.isEmpty()) {
            return null
        }

        val item = TodoItem(
            title = cleanTitle
        )

        val writer = cloudSyncWriter

        return if (writer == null) {
            val id = todoDao.insert(item)
            item.copy(id = id)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.TODO,
                entityId = item.syncId
            ) { hlc ->
                val stamped = item.copy(syncHlc = hlc)
                val id = todoDao.insert(stamped)

                CloudSyncMutation(
                    value = stamped.copy(id = id),
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    suspend fun setDone(
        item: TodoItem,
        done: Boolean
    ) {
        val updated = item.copy(
            isDone = done,
            completedAt = if (done) System.currentTimeMillis() else null
        )

        val writer = cloudSyncWriter

        if (writer == null) {
            todoDao.update(updated)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.TODO,
                entityId = item.syncId
            ) { hlc ->
                val stamped = updated.copy(syncHlc = hlc)
                todoDao.update(stamped)

                CloudSyncMutation(
                    value = Unit,
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    suspend fun deleteTodo(
        item: TodoItem
    ) {
        val writer = cloudSyncWriter

        if (writer == null) {
            todoDao.delete(item)
        } else {
            writer.delete(
                entityType = CloudSyncEntityTypes.TODO,
                entityId = item.syncId
            ) {
                todoDao.delete(item)
            }
        }
    }
}

