
package com.health.friday.tasks

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class TodoTool(
    private val repository: TodoRepository
) : AiTool {

    override val name = "todo"

    override val description =
        "Manages the user's TODO tasks. Can list tasks, add a task, " +
                "complete a task, or delete a task."

    override val parameters = listOf(
        AiToolParameter(
            name = "action",
            description =
                "What to do: list, add, complete, or delete.",
            required = true
        ),
        AiToolParameter(
            name = "title",
            description =
                "The task title. Required for add, complete, and delete. " +
                        "For complete/delete, use the task title as it appears " +
                        "in the user's TODO list."
        )
    )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val action =
            arguments["action"]
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val title =
            arguments["title"]
                ?.trim()
                .orEmpty()

        return when (action) {

            "list" -> {
                listTodos()
            }

            "add" -> {
                addTodo(title)
            }

            "complete" -> {
                setTodoDone(
                    title = title,
                    done = true
                )
            }

            "delete" -> {
                deleteTodo(title)
            }

            else -> {
                "Nothing changed. The TODO action must be " +
                        "list, add, complete, or delete."
            }
        }
    }

    private suspend fun listTodos(): String {

        val todos =
            repository.getTodosNow()

        if (todos.isEmpty()) {
            return "There are no TODO tasks."
        }

        return buildString {

            append("TODOs:\n")

            for (todo in todos) {

                val status =
                    if (todo.isDone) {
                        "completed"
                    } else {
                        "open"
                    }

                append(
                    "• ${todo.title} — $status\n"
                )
            }
        }.trimEnd()
    }

    private suspend fun addTodo(
        title: String
    ): String {

        if (title.isBlank()) {
            return "Nothing added. No TODO title was given."
        }

        val existing =
            repository.getTodosNow()

        val duplicate =
            existing.firstOrNull {
                it.title.equals(
                    title.trim(),
                    ignoreCase = true
                ) && !it.isDone
            }

        if (duplicate != null) {
            return "That TODO already exists: \"${duplicate.title}\"."
        }

        val saved =
            repository.addTodo(title)

        if (saved == null) {
            return "Nothing added. The TODO title was blank."
        }

        return "Added TODO: \"${saved.title}\"."
    }

    private suspend fun setTodoDone(
        title: String,
        done: Boolean
    ): String {

        if (title.isBlank()) {
            return "Nothing changed. No TODO title was given."
        }

        val todos =
            repository.getTodosNow()

        val match =
            findTodo(
                todos = todos,
                title = title
            )

        if (match == null) {
            return noMatchingTodo(
                title = title,
                todos = todos
            )
        }

        if (match.isDone == done) {
            return if (done) {
                "That TODO is already completed: \"${match.title}\"."
            } else {
                "That TODO is already open: \"${match.title}\"."
            }
        }

        repository.setDone(
            item = match,
            done = done
        )

        return "Completed TODO: \"${match.title}\"."
    }

    private suspend fun deleteTodo(
        title: String
    ): String {

        if (title.isBlank()) {
            return "Nothing deleted. No TODO title was given."
        }

        val todos =
            repository.getTodosNow()

        val match =
            findTodo(
                todos = todos,
                title = title
            )

        if (match == null) {
            return noMatchingTodo(
                title = title,
                todos = todos
            )
        }

        repository.deleteTodo(match)

        return "Deleted TODO: \"${match.title}\"."
    }

    private fun findTodo(
        todos: List<com.health.friday.data.local.TodoItem>,
        title: String
    ): com.health.friday.data.local.TodoItem? {

        val cleanTitle =
            title.trim()

        val exact =
            todos.firstOrNull {
                it.title.equals(
                    cleanTitle,
                    ignoreCase = true
                )
            }

        if (exact != null) {
            return exact
        }

        val containing =
            todos.filter {
                it.title.contains(
                    cleanTitle,
                    ignoreCase = true
                ) ||
                        cleanTitle.contains(
                            it.title,
                            ignoreCase = true
                        )
            }

        return if (containing.size == 1) {
            containing.first()
        } else {
            null
        }
    }

    private fun noMatchingTodo(
        title: String,
        todos: List<com.health.friday.data.local.TodoItem>
    ): String {

        if (todos.isEmpty()) {
            return "No TODO named \"$title\" exists because the TODO list is empty."
        }

        val openTodos =
            todos
                .filter { !it.isDone }
                .map { it.title }

        if (openTodos.isEmpty()) {
            return "I couldn't find an open TODO named \"$title\"."
        }

        return "I couldn't uniquely identify \"$title\". " +
                "Open TODOs are: ${openTodos.joinToString(", ")}."
    }
}

