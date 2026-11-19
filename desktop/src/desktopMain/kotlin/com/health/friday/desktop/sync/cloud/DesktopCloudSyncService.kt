package com.health.friday.desktop.sync.cloud

import com.health.friday.desktop.sync.drive.DesktopDriveVaultClient
import com.health.friday.desktop.sync.drive.DesktopGoogleDriveOAuth
import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Windows-stage integration for shared FRIDAY key recovery.
 * This service does not yet upload or apply data batches; that follows once the
 * desktop repository has durable outbox/inbox storage and a remote applier.
 */
class DesktopCloudSyncService internal constructor(
    private val oauth: DesktopGoogleDriveOAuth = DesktopGoogleDriveOAuth(),
    private val keyVault: DesktopCloudSyncKeyVault = DesktopCloudSyncKeyVault(),
    private val security: DesktopCloudSyncSecurity = DesktopCloudSyncSecurity(),
    private val drive: DesktopDriveVaultClient = DesktopDriveVaultClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun hasSavedAuthorization(): Boolean = oauth.hasSavedAuthorization()

    fun hasLocalMasterKey(): Boolean = keyVault.hasKey()

    /** Opens Google authorization once and verifies access to the shared vault. */
    fun connect(credentialsFile: File): String {
        oauth.connect(credentialsFile)
        val token = oauth.accessToken()
        val folderId = drive.ensureVaultFolder(token)
        return "Google Drive authorized. Shared FRIDAY_Vault is accessible (folder ID: $folderId)."
    }

    /** Creates/persists a master key if needed, then uploads its passphrase-wrapped envelope. */
    fun backUpRecoveryKey(passphrase: CharArray): String {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Recovery phrase must contain at least $MIN_PASSPHRASE_LENGTH characters."
        }
        val token = oauth.accessToken()
        val folderId = drive.ensureVaultFolder(token)
        val remoteFile = drive.findLatestRecoveryEnvelope(token, folderId)
        val existing = keyVault.load()

        // Never create a different Windows key over an existing Android/FRIDAY
        // recovery envelope. That would make the newest envelope disagree with
        // already-uploaded batches. Restore the existing envelope first.
        if (existing == null && remoteFile != null) {
            error(
                "A FRIDAY recovery envelope already exists in Drive. Restore it on Windows first; " +
                    "creating a new Windows key here would disconnect this device from existing batches."
            )
        }

        if (existing == null) {
            val (newMaster, envelope) = security.createNewMasterKey(passphrase)
            try {
                keyVault.save(newMaster)
                uploadEnvelope(token, folderId, envelope)
                return "Windows recovery key backed up. Key ID: ${envelope.keyId}. The local master key is DPAPI protected."
            } finally {
                newMaster.destroy()
            }
        }

        try {
            if (remoteFile != null) {
                val remoteEnvelope = json.decodeFromString<CloudSyncKeyEnvelope>(
                    drive.downloadTextFile(token, remoteFile.id)
                )
                remoteEnvelope.validate()
                require(remoteEnvelope.keyId == existing.keyId) {
                    "The latest Drive recovery envelope belongs to a different FRIDAY key. " +
                        "Refusing to replace it with this PC's key. Restore or reconcile the keys first."
                }
            }
            val envelope = security.createRecoveryEnvelope(existing, passphrase)
            uploadEnvelope(token, folderId, envelope)
            return "Windows recovery key backed up. Key ID: ${envelope.keyId}. The existing local key was retained."
        } finally {
            existing.destroy()
        }
    }

    /** Restores Android's portable envelope, without overwriting a different local key. */
    fun restoreRecoveryKey(passphrase: CharArray): String {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Recovery phrase must contain at least $MIN_PASSPHRASE_LENGTH characters."
        }
        val token = oauth.accessToken()
        val folderId = drive.ensureVaultFolder(token)
        val file = drive.findLatestRecoveryEnvelope(token, folderId)
            ?: error("No FRIDAY recovery envelope was found in FRIDAY_Vault.")
        val content = drive.downloadTextFile(token, file.id)
        val envelope = json.decodeFromString<CloudSyncKeyEnvelope>(content)
        envelope.validate()

        val recovered = security.restoreMasterKey(envelope, passphrase)
        try {
            val current = keyVault.load()
            if (current != null) {
                try {
                    require(current.keyId == recovered.keyId) {
                        "This PC already has a different FRIDAY sync key. The existing key was not replaced."
                    }
                    val currentBytes = current.copyBytes()
                    val recoveredBytes = recovered.copyBytes()
                    try {
                        require(MessageDigest.isEqual(currentBytes, recoveredBytes)) {
                            "The stored key ID matches, but the key material differs. Existing key was not replaced."
                        }
                    } finally {
                        currentBytes.fill(0)
                        recoveredBytes.fill(0)
                    }
                    return "Recovery key already matches this PC. No key was replaced. Key ID: ${recovered.keyId}."
                } finally {
                    current.destroy()
                }
            }

            keyVault.save(recovered)
            return "Recovery key restored to this Windows account. Key ID: ${recovered.keyId}. The local key is DPAPI protected."
        } finally {
            recovered.destroy()
        }
    }

    private fun uploadEnvelope(
        accessToken: String,
        folderId: String,
        envelope: CloudSyncKeyEnvelope
    ) {
        val content = json.encodeToString(envelope)
        drive.uploadRecoveryEnvelope(
            accessToken = accessToken,
            folderId = folderId,
            createdAtMs = envelope.createdAtMs,
            content = content,
            keyId = envelope.keyId
        )
    }

    private companion object {
        const val MIN_PASSPHRASE_LENGTH = 16
    }
}
