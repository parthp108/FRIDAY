package com.health.friday.sync.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DriveSyncState {
    IDLE,
    SYNCING,
    AUTH_REQUIRED,
    SUCCESS,
    FAILED
}

data class DriveSyncStatus(
    val state: DriveSyncState = DriveSyncState.IDLE,
    val lastSyncTimeMs: Long = 0L,
    val errorMessage: String? = null,
    val accountEmail: String? = null
)

class DriveSyncStatusRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "friday_drive_sync_status",
        Context.MODE_PRIVATE
    )

    private val _status = MutableStateFlow(loadStatus())
    val status: StateFlow<DriveSyncStatus> = _status.asStateFlow()

    private fun loadStatus(): DriveSyncStatus {
        val stateName = prefs.getString("state", DriveSyncState.IDLE.name) ?: DriveSyncState.IDLE.name
        val state = runCatching { DriveSyncState.valueOf(stateName) }.getOrDefault(DriveSyncState.IDLE)
        val lastSync = prefs.getLong("last_sync_ms", 0L)
        val errorMsg = prefs.getString("error_message", null)
        val email = prefs.getString("account_email", null)
        return DriveSyncStatus(state, lastSync, errorMsg, email)
    }

    fun updateStatus(state: DriveSyncState, errorMessage: String? = null, accountEmail: String? = null) {
        val now = System.currentTimeMillis()
        val lastSync = if (state == DriveSyncState.SUCCESS) now else _status.value.lastSyncTimeMs
        val email = accountEmail ?: _status.value.accountEmail
        val newStatus = DriveSyncStatus(state, lastSync, errorMessage, email)
        _status.value = newStatus

        prefs.edit()
            .putString("state", state.name)
            .putLong("last_sync_ms", lastSync)
            .putString("error_message", errorMessage)
            .putString("account_email", email)
            .apply()
    }

    fun clearAuthRequired() {
        if (_status.value.state == DriveSyncState.AUTH_REQUIRED) {
            updateStatus(DriveSyncState.IDLE)
        }
    }

    fun clearAll() {
        _status.value = DriveSyncStatus()
        prefs.edit().clear().apply()
    }
}
