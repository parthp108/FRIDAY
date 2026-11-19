package com.health.friday.desktop.sync.cloud

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Persists the FRIDAY AES-256 master key under the current Windows account
 * using DPAPI. The portable recovery envelope is still required for migration
 * to another device or Windows installation.
 */
class DesktopCloudSyncKeyVault internal constructor(
    private val store: DpapiFileStore = DpapiFileStore(
        DpapiFileStore.appDataFile(KEY_FILE_NAME)
    )
) {
    fun load(): PortableCloudMasterKey? {
        val plain = store.read() ?: return null
        try {
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                require(input.readUTF() == MAGIC) { "Invalid FRIDAY desktop key-vault header." }
                require(input.readInt() == FORMAT_VERSION) { "Unsupported FRIDAY desktop key-vault version." }
                val keyId = input.readUTF()
                require(keyId.isNotBlank()) { "Desktop cloud key ID is empty." }
                val length = input.readInt()
                require(length == MASTER_KEY_BYTES) { "Desktop cloud key has an invalid length." }
                val keyBytes = ByteArray(length)
                input.readFully(keyBytes)
                require(input.available() == 0) { "Unexpected trailing data in the desktop key vault." }
                return PortableCloudMasterKey(keyId, keyBytes)
            }
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Creates the local key file once. Re-saving the exact same key is safe;
     * silently replacing a different key is intentionally prohibited.
     */
    fun save(masterKey: PortableCloudMasterKey) {
        val current = load()
        if (current != null) {
            val currentBytes = current.copyBytes()
            val candidateBytes = masterKey.copyBytes()
            try {
                require(current.keyId == masterKey.keyId &&
                    MessageDigest.isEqual(currentBytes, candidateBytes)) {
                    "A different FRIDAY cloud key is already stored on this PC. Restore/reset must be handled explicitly."
                }
                return
            } finally {
                currentBytes.fill(0)
                candidateBytes.fill(0)
                current.destroy()
            }
        }

        val keyBytes = masterKey.copyBytes()
        val plain = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(masterKey.keyId)
                output.writeInt(keyBytes.size)
                output.write(keyBytes)
            }
            buffer.toByteArray()
        }
        try {
            store.write(plain)
        } finally {
            keyBytes.fill(0)
            plain.fill(0)
        }
    }

    fun hasKey(): Boolean {
        val key = load() ?: return false
        key.destroy()
        return true
    }

    companion object {
        private const val KEY_FILE_NAME = "cloud-master-key.dpapi"
        private const val MAGIC = "FRIDAY_DESKTOP_CLOUD_KEY"
        private const val FORMAT_VERSION = 1
        private const val MASTER_KEY_BYTES = 32
    }
}
