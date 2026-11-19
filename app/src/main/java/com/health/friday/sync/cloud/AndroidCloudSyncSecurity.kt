package com.health.friday.sync.cloud

import android.content.Context
import com.health.friday.shared.sync.cloud.E2EE_MAGIC_HEADER
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AndroidCloudSyncSecurity(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs by lazy { context.getSharedPreferences("friday_cloud_security_prefs", Context.MODE_PRIVATE) }

    fun getOrGeneratePassphrase(): String {
        var passphrase = prefs.getString("cloud_sync_passphrase", null)
        if (passphrase.isNullOrBlank()) {
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            passphrase = Base64.getEncoder().encodeToString(randomBytes)
            prefs.edit().putString("cloud_sync_passphrase", passphrase).apply()
        }
        return passphrase
    }

    fun encryptBatch(batch: SyncBatch, passphrase: String = getOrGeneratePassphrase()): EncryptedSyncPayload {
        batch.validate()
        val plainTextJson = json.encodeToString(SyncBatch.serializer(), batch)
        val plainTextBytes = plainTextJson.toByteArray(Charsets.UTF_8)

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val aesKey = deriveAesKey(passphrase, salt)
        val hmacKey = deriveHmacKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val ciphertext = cipher.doFinal(plainTextBytes)

        val hmac = computeHmac(hmacKey, ciphertext)

        val encoder = Base64.getEncoder()

        return EncryptedSyncPayload(
            magicHeader = E2EE_MAGIC_HEADER,
            saltBase64 = encoder.encodeToString(salt),
            ivBase64 = encoder.encodeToString(iv),
            ciphertextBase64 = encoder.encodeToString(ciphertext),
            hmacBase64 = encoder.encodeToString(hmac)
        ).also { it.validate() }
    }

    fun decryptBatch(payload: EncryptedSyncPayload, passphrase: String = getOrGeneratePassphrase()): SyncBatch {
        payload.validate()

        val decoder = Base64.getDecoder()

        val salt = decoder.decode(payload.saltBase64)
        val iv = decoder.decode(payload.ivBase64)
        val ciphertext = decoder.decode(payload.ciphertextBase64)
        val expectedHmac = decoder.decode(payload.hmacBase64)

        val aesKey = deriveAesKey(passphrase, salt)
        val hmacKey = deriveHmacKey(passphrase, salt)

        val computedHmac = computeHmac(hmacKey, ciphertext)
        require(computedHmac.contentEquals(expectedHmac)) {
            "Cloud sync payload HMAC authentication failed: payload tampered or key mismatch"
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val plainTextBytes = cipher.doFinal(ciphertext)

        val plainTextJson = String(plainTextBytes, Charsets.UTF_8)
        val batch = json.decodeFromString(SyncBatch.serializer(), plainTextJson)
        batch.validate()
        return batch
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun deriveHmacKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "HmacSHA256")
    }

    private fun computeHmac(key: SecretKeySpec, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(data)
    }
}
