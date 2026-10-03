
package com.health.friday.tasks

import com.health.friday.data.local.TodoDao
import com.health.friday.data.local.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TodoRepository(
    private val todoDao: TodoDao
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

        val id = todoDao.insert(item)

        return item.copy(id = id)
    }

    suspend fun setDone(
        item: TodoItem,
        done: Boolean
    ) {
        todoDao.update(
            item.copy(
                isDone = done,
                completedAt =
                    if (done) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
            )
        )
    }

    suspend fun deleteTodo(
        item: TodoItem
    ) {
        todoDao.delete(item)
    }
}

