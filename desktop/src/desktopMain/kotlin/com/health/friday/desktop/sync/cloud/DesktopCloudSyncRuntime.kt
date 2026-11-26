package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.sync.drive.DesktopDriveVaultClient
import com.health.friday.desktop.sync.drive.DesktopGoogleDriveOAuth
import com.health.friday.shared.sync.cloud.DebouncedSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DesktopSyncState { IDLE, SYNCING, SUCCESS, FAILED, AUTH_REQUIRED }

data class DesktopSyncStatus(
    val state: DesktopSyncState = DesktopSyncState.IDLE,
    val lastSyncMs: Long = 0L,
    val errorMessage: String? = null
)

/** Shares the same OAuth token store, DPAPI key vault and Drive client for settings and sync. */
class DesktopCloudSyncRuntime(
    deviceId: String,
    remoteApplier: DesktopCloudSyncRemoteApplier
) {
    private val oauth = DesktopGoogleDriveOAuth()
    private val keyVault = DesktopCloudSyncKeyVault()
    private val security = DesktopCloudSyncSecurity()
    private val drive = DesktopDriveVaultClient()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(DesktopSyncStatus())
    val syncStatus: StateFlow<DesktopSyncStatus> = _syncStatus.asStateFlow()

    val store: DesktopCloudSyncStore = DesktopCloudSyncStore(
        deviceId = deviceId,
        remoteApplier = remoteApplier,
        onOperationEnqueued = { triggerInstantSync() }
    )

    val recoveryService = DesktopCloudSyncService(
        oauth = oauth,
        keyVault = keyVault,
        security = security,
        drive = drive
    )

    val syncEngine = DesktopDriveSyncEngine(
        deviceId = deviceId,
        oauth = oauth,
        keyVault = keyVault,
        security = security,
        drive = drive,
        store = store
    )

    suspend fun runSync(): DesktopDriveSyncResult {
        if (!oauth.hasSavedAuthorization() || !keyVault.hasKey()) {
            val state = if (!oauth.hasSavedAuthorization()) DesktopSyncState.AUTH_REQUIRED else DesktopSyncState.IDLE
            _syncStatus.value = _syncStatus.value.copy(state = state, errorMessage = "Authorization or key required")
            error("Authorization or key required")
        }
        _syncStatus.value = _syncStatus.value.copy(state = DesktopSyncState.SYNCING)
        return try {
            val res = syncEngine.performSync()
            val now = System.currentTimeMillis()
            _syncStatus.value = DesktopSyncStatus(
                state = DesktopSyncState.SUCCESS,
                lastSyncMs = now,
                errorMessage = null
            )
            res
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            val isAuth = msg.contains("401") || msg.contains("auth", ignoreCase = true)
            val state = if (isAuth) DesktopSyncState.AUTH_REQUIRED else DesktopSyncState.FAILED
            _syncStatus.value = _syncStatus.value.copy(state = state, errorMessage = msg)
            throw e
        }
    }

    private val instantSyncTrigger by lazy {
        DebouncedSyncTrigger(
            scope = syncScope,
            onFailure = { error ->
                System.err.println(
                    "FRIDAY Desktop sync failed: ${error.message}"
                )
            },
            action = {
                if (oauth.hasSavedAuthorization() && keyVault.hasKey()) {
                    runCatching { runSync() }
                }
            }
        )
    }

    fun triggerInstantSync() {
        if (!oauth.hasSavedAuthorization() || !keyVault.hasKey()) return

        instantSyncTrigger.request()
    }

    fun startBackgroundSync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(BACKGROUND_SYNC_INTERVAL_MS)
                if (oauth.hasSavedAuthorization() && keyVault.hasKey()) {
                    runCatching {
                        runSync()
                    }.onFailure { error ->
                        System.err.println(
                            "FRIDAY Desktop background sync failed: ${error.message}"
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val BACKGROUND_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }
}
