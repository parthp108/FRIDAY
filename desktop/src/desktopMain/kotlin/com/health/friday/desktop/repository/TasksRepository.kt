package com.health.friday.desktop.repository

import com.health.friday.shared.models.FridayReminderDto
import com.health.friday.shared.models.FridayTaskDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TasksRepository(private val onReadOnly: (String) -> Unit) {
    private val _tasks = MutableStateFlow<List<FridayTaskDto>>(emptyList())
    val tasks: StateFlow<List<FridayTaskDto>> = _tasks

    private val _reminders = MutableStateFlow<List<FridayReminderDto>>(emptyList())
    val reminders: StateFlow<List<FridayReminderDto>> = _reminders

    fun setTasks(fetched: List<FridayTaskDto>) { _tasks.value = fetched }
    fun setReminders(fetched: List<FridayReminderDto>) { _reminders.value = fetched }

    @Suppress("UNUSED_PARAMETER")
    fun addTask(title: String) = onReadOnly("Adding a task")

    // The old version re-sent ADD_TASK on every toggle, creating a duplicate on the phone each time.
    @Suppress("UNUSED_PARAMETER")
    fun toggleTask(task: FridayTaskDto) = onReadOnly("Changing a task")
}
