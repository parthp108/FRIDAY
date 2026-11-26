package com.health.friday.desktop.features.hud

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.health.friday.desktop.repository.DesktopRepository
import com.health.friday.desktop.sync.cloud.DesktopDriveSyncResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HudScreenModel(
    private val repository: DesktopRepository
) : StateScreenModel<HudState>(HudState()) {

    init {
        screenModelScope.launch(Dispatchers.IO) {
            repository.initSync()
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.messages.collect { msgs ->
                updateState { it.copy(messages = msgs) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.sessions.collect { sess ->
                updateState { it.copy(sessions = sess) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.currentSessionId.collect { id ->
                updateState { it.copy(currentSessionId = id) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.tasks.collect { t ->
                updateState { it.copy(tasks = t) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.goals.collect { g ->
                updateState { it.copy(goals = g) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.journalEntries.collect { entries ->
                updateState { it.copy(journalEntries = entries) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.healthArchiveRecords.collect { records ->
                updateState { it.copy(healthArchiveRecords = records) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.reminders.collect { r ->
                updateState { it.copy(reminders = r) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.meals.collect { m ->
                updateState { it.copy(meals = m) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.dailySignals.collect { s ->
                updateState { it.copy(dailySignals = s) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.isPhoneConnected.collect { connected ->
                updateState { it.copy(isPhoneConnected = connected) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.phoneName.collect { name ->
                updateState { it.copy(phoneName = name) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.apiKey.collect { key ->
                updateState { it.copy(apiKey = key) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.personaStyle.collect { persona ->
                updateState { it.copy(personaStyle = persona) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.localModelName.collect { model ->
                updateState { it.copy(activeModel = "Local Ollama ($model)") }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.notice.collect { note ->
                updateState { it.copy(notice = note) }
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            repository.desktopSyncStatus.collect { status ->
                updateState { it.copy(desktopSyncStatus = status, isDriveConnected = repository.isDriveConnected) }
            }
        }
    }

    fun connectGoogleDrive(credentialsFile: File): String = repository.connectGoogleDrive(credentialsFile)
    fun backUpGoogleDriveRecoveryKey(passphrase: CharArray): String = repository.backUpGoogleDriveRecoveryKey(passphrase)
    fun restoreGoogleDriveRecoveryKey(passphrase: CharArray): String = repository.restoreGoogleDriveRecoveryKey(passphrase)
    suspend fun syncGoogleDrive(): DesktopDriveSyncResult = repository.syncGoogleDrive()

    fun processIntent(intent: HudIntent) {
        when (intent) {
            is HudIntent.ChangeTab -> updateState { it.copy(selectedTab = intent.tab) }
            is HudIntent.DismissNotice -> repository.clearNotice()
            is HudIntent.ToggleHudMode -> { /* Handled in UI overlay */ }

            is HudIntent.SendMessage -> repository.sendMessage(intent.message)
            is HudIntent.NewChat -> repository.createNewSession()
            is HudIntent.SelectSession -> repository.switchSession(intent.sessionId)
            is HudIntent.DeleteSession -> repository.deleteSession(intent.sessionId)
            is HudIntent.ClearHistory -> repository.clearAllChatSessions()

            is HudIntent.AddTask -> repository.addTask(intent.taskTitle)
            is HudIntent.ToggleTask -> repository.toggleTask(intent.task)
            is HudIntent.ToggleGoal -> repository.toggleGoal(intent.goal)
            is HudIntent.AddJournalEntry -> repository.addJournalEntry(intent.text)
            is HudIntent.AddReminder -> repository.addReminder()
            is HudIntent.LogMeal -> repository.logMeal(intent.name, intent.cals, intent.p, intent.c, intent.f)
            is HudIntent.AddWater -> repository.addWater(intent.amountMl)
            is HudIntent.UpdateSettings -> repository.updateSettings(intent.apiKey, intent.persona, intent.localModel)
        }
    }

    private fun updateState(transform: (HudState) -> HudState) {
        mutableState.value = transform(mutableState.value)
    }
}
