package com.health.friday.desktop.repository

import com.health.friday.shared.models.FridayReminderDto
import com.health.friday.shared.models.FridayTaskDto
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import com.health.friday.shared.sync.cloud.JournalSyncPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TasksRepository(private val onReadOnly: (String) -> Unit) {
    private val _tasks = MutableStateFlow<List<FridayTaskDto>>(emptyList())
    val tasks: StateFlow<List<FridayTaskDto>> = _tasks
    private var phoneTasks: List<FridayTaskDto> = emptyList()
    private var cloudTasks: List<FridayTaskDto> = emptyList()
    private var deletedCloudTaskIds: Set<String> = emptySet()

    private val _goals = MutableStateFlow<List<GoalSyncPayload>>(emptyList())
    val goals: StateFlow<List<GoalSyncPayload>> = _goals

    private val _journalEntries = MutableStateFlow<List<JournalSyncPayload>>(emptyList())
    val journalEntries: StateFlow<List<JournalSyncPayload>> = _journalEntries

    private val _reminders = MutableStateFlow<List<FridayReminderDto>>(emptyList())
    val reminders: StateFlow<List<FridayReminderDto>> = _reminders

    @Synchronized
    fun setTasks(fetched: List<FridayTaskDto>) {
        phoneTasks = fetched
        publishTasks()
    }

    @Synchronized
    fun setCloudTasks(fetched: List<FridayTaskDto>, deletedIds: Set<String> = emptySet()) {
        cloudTasks = fetched
        deletedCloudTaskIds = deletedIds
        publishTasks()
    }

    private fun publishTasks() {
        // Do not let a stale USB snapshot reinsert a task with a Drive tombstone.
        _tasks.value = (phoneTasks + cloudTasks)
            .filterNot { it.id in deletedCloudTaskIds }
            .associateBy { it.id }.values.toList()
    }

    fun setCloudGoals(fetched: List<GoalSyncPayload>) { _goals.value = fetched }
    fun setCloudJournalEntries(fetched: List<JournalSyncPayload>) { _journalEntries.value = fetched }

    fun setReminders(fetched: List<FridayReminderDto>) { _reminders.value = fetched }

    @Suppress("UNUSED_PARAMETER")
    fun addTask(title: String) = onReadOnly("Adding a task")

    @Suppress("UNUSED_PARAMETER")
    fun toggleTask(task: FridayTaskDto) = onReadOnly("Changing a task")
}
