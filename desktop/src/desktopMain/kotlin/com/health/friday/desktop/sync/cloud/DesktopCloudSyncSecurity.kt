package com.health.friday.desktop.sync.cloud

import com.health.friday.shared.sync.cloud.CLOUD_SYNC_KEY_ENVELOPE_MAGIC
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_KEY_ENVELOPE_VERSION
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_RECOVERY_KDF
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_RECOVERY_KDF_ITERATIONS
import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import com.health.friday.shared.sync.cloud.E2EE_MAGIC_HEADER_V2
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import java.security.SecureRandom
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM/Windows implementation of the same portable envelope and batch format
 * used by AndroidCloudSyncSecurity.
 *
 * This class deliberately keeps a restored key in memory only. A Windows
 * DPAPI-backed local vault should be added before using persistent desktop
 * sync; do not save PortableCloudMasterKey.bytes to a normal config file.
 */
class PortableCloudMasterKey internal constructor(
    val keyId: String,
    private val keyBytes: ByteArray
) {
    internal fun copyBytes(): ByteArray = keyBytes.copyOf()

    fun destroy() {
        keyBytes.fill(0)
    }
}

class DesktopCloudSyncSecurity {

    fun createNewMasterKey(
        passphrase: CharArray,
        createdAtMs: Long = System.currentTimeMillis()
    ): Pair<PortableCloudMasterKey, CloudSyncKeyEnvelope> {
        validatePassphrase(passphrase)

        val masterBytes = randomBytes(MASTER_KEY_BYTES)
        val keyId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes(16))
        val master = PortableCloudMasterKey(keyId, masterBytes.copyOf())
        val envelope = createEnvelope(masterBytes, keyId, passphrase, createdAtMs)
        masterBytes.fill(0)
        return master to envelope
    }

    fun restoreMasterKey(
        envelope: CloudSyncKeyEnvelope,
        passphrase: CharArray
    ): PortableCloudMasterKey {
        envelope.validate()
        validatePassphrase(passphrase)

        val salt = decode(envelope.saltBase64)
        val iv = decode(envelope.ivBase64)
        val wrapped = decode(envelope.wrappedMasterKeyBase64)
        require(salt.size == SALT_BYTES) { "Invalid envelope salt length" }
        require(iv.size == GCM_IV_BYTES) { "Invalid envelope IV length" }

        val wrappingKey = deriveRecoveryKey(passphrase, salt)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            wrappingKey,
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        cipher.updateAAD(envelopeAad(envelope.keyId))

        val masterBytes = cipher.doFinal(wrapped)
        require(masterBytes.size == MASTER_KEY_BYTES) {
            "Recovered FRIDAY master key has an invalid length"
        }
        return PortableCloudMasterKey(envelope.keyId, masterBytes)
    }

    fun createRecoveryEnvelope(
        masterKey: PortableCloudMasterKey,
        passphrase: CharArray,
        createdAtMs: Long = System.currentTimeMillis()
    ): CloudSyncKeyEnvelope {
        validatePassphrase(passphrase)
        val masterBytes = masterKey.copyBytes()
        return try {
            createEnvelope(
                masterBytes = masterBytes,
                keyId = masterKey.keyId,
                passphrase = passphrase,
                createdAtMs = createdAtMs
            )
        } finally {
            masterBytes.fill(0)
        }
    }

    fun encryptBatch(
        batch: SyncBatch,
        masterKey: PortableCloudMasterKey
    ): EncryptedSyncPayload {
        batch.validate()
        val iv = randomBytes(GCM_IV_BYTES)
        val keyBytes = masterKey.copyBytes()
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(batchAad(masterKey.keyId))

            val plaintext = kotlinx.serialization.json.Json {
                encodeDefaults = true
            }.encodeToString(SyncBatch.serializer(), batch)
                .toByteArray(Charsets.UTF_8)

            val ciphertext = try {
                cipher.doFinal(plaintext)
            } finally {
                plaintext.fill(0)
            }

            return EncryptedSyncPayload(
                magicHeader = E2EE_MAGIC_HEADER_V2,
                ivBase64 = b64(iv),
                ciphertextBase64 = b64(ciphertext),
                keyId = masterKey.keyId
            ).also { it.validate() }
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decryptBatch(
        payload: EncryptedSyncPayload,
        masterKey: PortableCloudMasterKey
    ): SyncBatch {
        payload.validate()
        require(payload.magicHeader == E2EE_MAGIC_HEADER_V2) {
            "Desktop crypto currently accepts V2 batches only"
        }
        require(payload.keyId == masterKey.keyId) {
            "This batch belongs to a different FRIDAY master key"
        }

        val iv = decode(payload.ivBase64)
        val ciphertext = decode(payload.ciphertextBase64)
        require(iv.size == GCM_IV_BYTES) { "Invalid batch IV length" }

        val keyBytes = masterKey.copyBytes()
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(batchAad(masterKey.keyId))

            val plaintext = cipher.doFinal(ciphertext)
            try {
                val batch = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(
                    SyncBatch.serializer(),
                    String(plaintext, Charsets.UTF_8)
                )
                batch.validate()
                return batch
            } finally {
                plaintext.fill(0)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createEnvelope(
        masterBytes: ByteArray,
        keyId: String,
        passphrase: CharArray,
        createdAtMs: Long
    ): CloudSyncKeyEnvelope {
        require(masterBytes.size == MASTER_KEY_BYTES)
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(GCM_IV_BYTES)
        val wrappingKey = deriveRecoveryKey(passphrase, salt)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            wrappingKey,
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        cipher.updateAAD(envelopeAad(keyId))

        val wrapped = cipher.doFinal(masterBytes)
        return CloudSyncKeyEnvelope(
            magicHeader = CLOUD_SYNC_KEY_ENVELOPE_MAGIC,
            version = CLOUD_SYNC_KEY_ENVELOPE_VERSION,
            keyId = keyId,
            kdf = CLOUD_SYNC_RECOVERY_KDF,
            iterations = CLOUD_SYNC_RECOVERY_KDF_ITERATIONS,
            saltBase64 = b64(salt),
            ivBase64 = b64(iv),
            wrappedMasterKeyBase64 = b64(wrapped),
            createdAtMs = createdAtMs
        ).also { it.validate() }
    }

    private fun deriveRecoveryKey(
        passphrase: CharArray,
        salt: ByteArray
    ): SecretKeySpec {
        val passwordCopy = passphrase.copyOf()
        val spec = PBEKeySpec(
            passwordCopy,
            salt,
            CLOUD_SYNC_RECOVERY_KDF_ITERATIONS,
            MASTER_KEY_BITS
        )
        return try {
            val bytes = SecretKeyFactory.getInstance(CLOUD_SYNC_RECOVERY_KDF)
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            passwordCopy.fill('\u0000')
        }
    }

    private fun validatePassphrase(passphrase: CharArray) {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Recovery passphrase must contain at least $MIN_PASSPHRASE_LENGTH characters"
        }
    }

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private fun b64(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(SecureRandom()::nextBytes)

    private fun envelopeAad(keyId: String): ByteArray =
        (
            "FRIDAY_MASTER_KEY_ENVELOPE|$CLOUD_SYNC_KEY_ENVELOPE_VERSION|" +
                "$keyId|$CLOUD_SYNC_RECOVERY_KDF|$CLOUD_SYNC_RECOVERY_KDF_ITERATIONS"
        ).toByteArray(Charsets.UTF_8)

    private fun batchAad(keyId: String): ByteArray =
        "FRIDAY_SYNC_BATCH|2|$keyId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MASTER_KEY_BYTES = 32
        private const val MASTER_KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MIN_PASSPHRASE_LENGTH = 16
    }
}
