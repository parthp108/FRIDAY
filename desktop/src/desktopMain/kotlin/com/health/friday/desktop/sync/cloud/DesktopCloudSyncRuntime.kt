package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.sync.drive.DesktopDriveVaultClient
import com.health.friday.desktop.sync.drive.DesktopGoogleDriveOAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shares the same OAuth token store, DPAPI key vault and Drive client for settings and sync. */
class DesktopCloudSyncRuntime(
    deviceId: String,
    remoteApplier: DesktopCloudSyncRemoteApplier
) {
    private val oauth = DesktopGoogleDriveOAuth()
    private val keyVault = DesktopCloudSyncKeyVault()
    private val security = DesktopCloudSyncSecurity()
    private val drive = DesktopDriveVaultClient()

    val store = DesktopCloudSyncStore(
        deviceId = deviceId,
        remoteApplier = remoteApplier
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

    fun startBackgroundSync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(BACKGROUND_SYNC_INTERVAL_MS)
                if (oauth.hasSavedAuthorization() && keyVault.hasKey()) {
                    runCatching {
                        syncEngine.performSync()
                    }
                }
            }
        }
    }

    private companion object {
        const val BACKGROUND_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }
}
