package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.sync.drive.DesktopDriveVaultClient
import com.health.friday.desktop.sync.drive.DesktopGoogleDriveOAuth

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
}
