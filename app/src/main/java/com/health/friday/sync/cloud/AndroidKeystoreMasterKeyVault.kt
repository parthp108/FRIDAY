package com.health.friday.sync.cloud

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the FRIDAY master key only after wrapping it with an AES key held
 * by Android Keystore. SharedPreferences contains ciphertext, not the key.
 */
internal class AndroidKeystoreMasterKeyVault(
    context: Context
) : LocalCloudMasterKeyVault {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyPassphraseIntoKeystore()
    }

    override fun readMasterKey(): StoredCloudMasterKey? {
        val keyId = prefs.getString(KEY_ID, null)
        val iv = prefs.getString(MASTER_IV, null)
        val ciphertext = prefs.getString(MASTER_CIPHERTEXT, null)

        if (keyId == null && iv == null && ciphertext == null) return null

        check(!keyId.isNullOrBlank() && !iv.isNullOrBlank() && !ciphertext.isNullOrBlank()) {
            "Incomplete local FRIDAY key material. Restore using the recovery envelope."
        }

        val plain = decryptLocal(
            ivBase64 = iv,
            ciphertextBase64 = ciphertext,
            aad = masterKeyAad(keyId)
        )
        check(plain.size == MASTER_KEY_BYTES) {
            "Local FRIDAY master key has an invalid length"
        }
        return StoredCloudMasterKey(keyId, plain)
    }

    override fun writeMasterKey(keyId: String, keyBytes: ByteArray) {
        require(keyId.isNotBlank())
        require(keyBytes.size == MASTER_KEY_BYTES)

        val (iv, ciphertext) = encryptLocal(
            plaintext = keyBytes,
            aad = masterKeyAad(keyId)
        )

        check(
            prefs.edit()
                .putString(KEY_ID, keyId)
                .putString(MASTER_IV, iv)
                .putString(MASTER_CIPHERTEXT, ciphertext)
                .commit()
        ) {
            "Could not persist the encrypted FRIDAY master key"
        }
    }

    override fun readLegacyPassphrase(): CharArray? {
        val iv = prefs.getString(LEGACY_IV, null) ?: return null
        val ciphertext = prefs.getString(LEGACY_CIPHERTEXT, null)
            ?: error("Incomplete legacy FRIDAY key material")

        val bytes = decryptLocal(
            ivBase64 = iv,
            ciphertextBase64 = ciphertext,
            aad = LEGACY_PASSPHRASE_AAD
        )
        return try {
            String(bytes, Charsets.UTF_8).toCharArray()
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Older FRIDAY versions saved cloud_sync_passphrase in plaintext.
     * Move it under Android Keystore protection, then remove the plaintext
     * preference. This keeps V1 decrypt support on the original device only.
     */
    private fun migrateLegacyPassphraseIntoKeystore() {
        val oldPassphrase = prefs.getString(OLD_PLAINTEXT_LEGACY_KEY, null) ?: return

        if (prefs.getString(LEGACY_CIPHERTEXT, null) == null) {
            val (iv, ciphertext) = encryptLocal(
                plaintext = oldPassphrase.toByteArray(Charsets.UTF_8),
                aad = LEGACY_PASSPHRASE_AAD
            )
            check(
                prefs.edit()
                    .putString(LEGACY_IV, iv)
                    .putString(LEGACY_CIPHERTEXT, ciphertext)
                    .commit()
            ) {
                "Could not migrate the legacy FRIDAY encryption key"
            }
        }

        check(prefs.edit().remove(OLD_PLAINTEXT_LEGACY_KEY).commit()) {
            "Could not remove the old plaintext FRIDAY encryption key"
        }
    }

    private fun encryptLocal(
        plaintext: ByteArray,
        aad: ByteArray
    ): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        return Base64.getEncoder().encodeToString(cipher.iv) to
            Base64.getEncoder().encodeToString(ciphertext)
    }

    private fun decryptLocal(
        ivBase64: String,
        ciphertextBase64: String,
        aad: ByteArray
    ): ByteArray {
        val iv = Base64.getDecoder().decode(ivBase64)
        val ciphertext = Base64.getDecoder().decode(ciphertextBase64)
        require(iv.size == GCM_IV_BYTES) { "Invalid local Keystore IV length" }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKeystoreKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun masterKeyAad(keyId: String): ByteArray =
        "FRIDAY_LOCAL_MASTER_KEY|1|$keyId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val PREFERENCES_NAME = "friday_cloud_security_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday_cloud_master_key_wrap_v1"

        private const val KEY_ID = "cloud_master_key_id"
        private const val MASTER_IV = "cloud_master_key_iv"
        private const val MASTER_CIPHERTEXT = "cloud_master_key_ciphertext"

        private const val OLD_PLAINTEXT_LEGACY_KEY = "cloud_sync_passphrase"
        private const val LEGACY_IV = "legacy_cloud_passphrase_iv"
        private const val LEGACY_CIPHERTEXT = "legacy_cloud_passphrase_ciphertext"
        private val LEGACY_PASSPHRASE_AAD =
            "FRIDAY_LEGACY_PASSPHRASE_V1".toByteArray(Charsets.UTF_8)

        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MASTER_KEY_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
