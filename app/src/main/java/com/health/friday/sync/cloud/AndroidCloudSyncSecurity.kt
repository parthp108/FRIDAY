package com.health.friday.sync.cloud

import android.content.Context
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_KEY_ENVELOPE_MAGIC
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_KEY_ENVELOPE_VERSION
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_RECOVERY_KDF
import com.health.friday.shared.sync.cloud.CLOUD_SYNC_RECOVERY_KDF_ITERATIONS
import com.health.friday.shared.sync.cloud.CloudSyncKeyEnvelope
import com.health.friday.shared.sync.cloud.E2EE_MAGIC_HEADER_V1
import com.health.friday.shared.sync.cloud.E2EE_MAGIC_HEADER_V2
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class StoredCloudMasterKey(
    val keyId: String,
    val bytes: ByteArray
)

internal interface LocalCloudMasterKeyVault {
    fun readMasterKey(): StoredCloudMasterKey?
    fun writeMasterKey(keyId: String, keyBytes: ByteArray)
    fun readLegacyPassphrase(): CharArray?
}

/**
 * Android-side crypto facade.
 *
 * The master key is generated once, stored locally only after Android Keystore
 * wrapping, and exported only inside a password-protected CloudSyncKeyEnvelope.
 */
class AndroidCloudSyncSecurity internal constructor(
    private val vault: LocalCloudMasterKeyVault
) {
    constructor(context: Context) : this(
        AndroidKeystoreMasterKeyVault(context.applicationContext)
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun hasLocalMasterKey(): Boolean {
        val stored = vault.readMasterKey() ?: return false
        stored.bytes.fill(0)
        return true
    }

    /**
     * Creates a portable recovery envelope for the current master key.
     * Calling this for the first time creates the master key.
     *
     * Require at least 16 characters. The app must never persist this
     * passphrase; the user must retain it independently.
     */
    fun createRecoveryEnvelope(
        passphrase: CharArray,
        createdAtMs: Long = System.currentTimeMillis()
    ): CloudSyncKeyEnvelope {
        validatePassphrase(passphrase)

        val stored = vault.readMasterKey() ?: run {
            val keyBytes = randomBytes(MASTER_KEY_BYTES)
            val newKey = StoredCloudMasterKey(
                keyId = newKeyId(),
                bytes = keyBytes
            )
            vault.writeMasterKey(newKey.keyId, newKey.bytes)
            newKey
        }

        try {
            require(stored.bytes.size == MASTER_KEY_BYTES) {
                "Stored FRIDAY master key has an invalid length"
            }

            val salt = randomBytes(SALT_BYTES)
            val iv = randomBytes(GCM_IV_BYTES)
            val wrappingKey = deriveRecoveryKey(passphrase, salt)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                wrappingKey,
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(envelopeAad(stored.keyId))

            val wrapped = cipher.doFinal(stored.bytes)

            return CloudSyncKeyEnvelope(
                magicHeader = CLOUD_SYNC_KEY_ENVELOPE_MAGIC,
                version = CLOUD_SYNC_KEY_ENVELOPE_VERSION,
                keyId = stored.keyId,
                kdf = CLOUD_SYNC_RECOVERY_KDF,
                iterations = CLOUD_SYNC_RECOVERY_KDF_ITERATIONS,
                saltBase64 = b64(salt),
                ivBase64 = b64(iv),
                wrappedMasterKeyBase64 = b64(wrapped),
                createdAtMs = createdAtMs
            ).also { it.validate() }
        } finally {
            stored.bytes.fill(0)
        }
    }

    /**
     * Restores the portable master key on a second Android installation.
     * It refuses to silently replace a different key already in use.
     */
    fun restoreFromEnvelope(
        envelope: CloudSyncKeyEnvelope,
        passphrase: CharArray
    ) {
        envelope.validate()
        validatePassphrase(passphrase)

        // A restored Android backup can contain the encrypted preference but
        // not the original Keystore key. In that case local decryption fails;
        // a successfully authenticated recovery envelope is allowed to repair it.
        val existing = runCatching { vault.readMasterKey() }.getOrNull()
        require(existing == null || existing.keyId == envelope.keyId) {
            "This installation already has a different FRIDAY sync key. " +
                "Do not overwrite it without an explicit key-reset workflow."
        }
        existing?.bytes?.fill(0)

        val salt = decodeBase64(envelope.saltBase64, "salt")
        val iv = decodeBase64(envelope.ivBase64, "IV")
        val wrapped = decodeBase64(envelope.wrappedMasterKeyBase64, "wrapped master key")

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

        val masterKeyBytes = cipher.doFinal(wrapped)
        try {
            require(masterKeyBytes.size == MASTER_KEY_BYTES) {
                "Recovered master key has an invalid length"
            }
            vault.writeMasterKey(envelope.keyId, masterKeyBytes)
        } finally {
            masterKeyBytes.fill(0)
        }
    }

    fun encryptBatch(batch: SyncBatch): EncryptedSyncPayload {
        batch.validate()
        val master = vault.readMasterKey()
            ?: error("FRIDAY cloud key is not initialized. Create or restore a recovery envelope first.")

        try {
            require(master.bytes.size == MASTER_KEY_BYTES) {
                "Stored FRIDAY master key has an invalid length"
            }

            val iv = randomBytes(GCM_IV_BYTES)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(master.bytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(batchAad(master.keyId))

            val plaintext = json.encodeToString(SyncBatch.serializer(), batch)
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
                keyId = master.keyId
            ).also { it.validate() }
        } finally {
            master.bytes.fill(0)
        }
    }

    fun decryptBatch(payload: EncryptedSyncPayload): SyncBatch {
        payload.validate()

        val plaintext = when (payload.magicHeader) {
            E2EE_MAGIC_HEADER_V2 -> decryptV2(payload)
            E2EE_MAGIC_HEADER_V1 -> decryptLegacyV1(payload)
            else -> error("Unsupported encrypted payload version")
        }

        val batch = try {
            json.decodeFromString(
                SyncBatch.serializer(),
                String(plaintext, Charsets.UTF_8)
            )
        } finally {
            plaintext.fill(0)
        }
        batch.validate()
        return batch
    }

    private fun decryptV2(payload: EncryptedSyncPayload): ByteArray {
        val master = vault.readMasterKey()
            ?: error("No local FRIDAY master key. Restore the recovery envelope first.")

        try {
            require(master.keyId == payload.keyId) {
                "This batch belongs to a different FRIDAY master key"
            }

            val iv = decodeBase64(payload.ivBase64, "IV")
            val ciphertext = decodeBase64(payload.ciphertextBase64, "ciphertext")
            require(iv.size == GCM_IV_BYTES) { "Invalid AES-GCM IV length" }

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(master.bytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(batchAad(master.keyId))
            return cipher.doFinal(ciphertext)
        } finally {
            master.bytes.fill(0)
        }
    }

    /**
     * Backward compatibility for V1 files created by this Android installation.
     * The previous implementation stored its random passphrase in plaintext
     * SharedPreferences. AndroidKeystoreMasterKeyVault migrates that value to
     * a Keystore-encrypted preference during initialization.
     */
    private fun decryptLegacyV1(payload: EncryptedSyncPayload): ByteArray {
        val legacyPassphrase = vault.readLegacyPassphrase()
            ?: error(
                "This is a legacy V1 batch, but its original installation key " +
                    "is unavailable. It cannot be recovered from this device."
            )

        try {
            val salt = decodeBase64(payload.saltBase64!!, "legacy salt")
            val iv = decodeBase64(payload.ivBase64, "legacy IV")
            val ciphertext = decodeBase64(payload.ciphertextBase64, "legacy ciphertext")
            val expectedHmac = decodeBase64(payload.hmacBase64!!, "legacy HMAC")

            val legacyKey = deriveLegacyV1Key(legacyPassphrase, salt)
            try {
                // V1 derived identical AES and HMAC key bytes. Verify the old
                // HMAC before attempting AES-GCM decryption.
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(legacyKey, "HmacSHA256"))
                val actualHmac = mac.doFinal(ciphertext)
                require(MessageDigest.isEqual(actualHmac, expectedHmac)) {
                    "Legacy V1 HMAC verification failed"
                }

                require(iv.size == GCM_IV_BYTES) { "Invalid legacy IV length" }
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(legacyKey, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                return cipher.doFinal(ciphertext)
            } finally {
                legacyKey.fill(0)
            }
        } finally {
            legacyPassphrase.fill('\u0000')
        }
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
            val keyBytes = SecretKeyFactory
                .getInstance(CLOUD_SYNC_RECOVERY_KDF)
                .generateSecret(spec)
                .encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            passwordCopy.fill('\u0000')
        }
    }

    private fun deriveLegacyV1Key(
        passphrase: CharArray,
        salt: ByteArray
    ): ByteArray {
        val passwordCopy = passphrase.copyOf()
        val spec = PBEKeySpec(passwordCopy, salt, LEGACY_V1_ITERATIONS, MASTER_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            passwordCopy.fill('\u0000')
        }
    }

    private fun validatePassphrase(passphrase: CharArray) {
        require(passphrase.size >= MIN_RECOVERY_PASSPHRASE_LENGTH) {
            "Recovery passphrase must contain at least $MIN_RECOVERY_PASSPHRASE_LENGTH characters"
        }
    }

    private fun decodeBase64(value: String, label: String): ByteArray =
        try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Base64 $label", e)
        }

    private fun b64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(SecureRandom()::nextBytes)

    private fun newKeyId(): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes(16))

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
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val SALT_BYTES = 16
        private const val LEGACY_V1_ITERATIONS = 100_000
        private const val MIN_RECOVERY_PASSPHRASE_LENGTH = 16
    }
}
