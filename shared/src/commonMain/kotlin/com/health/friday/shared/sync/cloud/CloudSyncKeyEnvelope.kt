package com.health.friday.shared.sync.cloud

import kotlinx.serialization.Serializable

const val CLOUD_SYNC_KEY_ENVELOPE_MAGIC = "FRIDAY_KEY_ENVELOPE_V1"
const val CLOUD_SYNC_KEY_ENVELOPE_VERSION = 1
const val CLOUD_SYNC_RECOVERY_KDF = "PBKDF2WithHmacSHA256"
const val CLOUD_SYNC_RECOVERY_KDF_ITERATIONS = 600_000

/**
 * Portable envelope containing the master key encrypted by a key derived
 * from the user's recovery passphrase.
 *
 * This JSON is safe to place in Drive: it does not contain the plaintext
 * master key or the recovery passphrase.
 */
@Serializable
data class CloudSyncKeyEnvelope(
    val magicHeader: String = CLOUD_SYNC_KEY_ENVELOPE_MAGIC,
    val version: Int = CLOUD_SYNC_KEY_ENVELOPE_VERSION,
    val keyId: String,
    val kdf: String = CLOUD_SYNC_RECOVERY_KDF,
    val iterations: Int = CLOUD_SYNC_RECOVERY_KDF_ITERATIONS,
    val saltBase64: String,
    val ivBase64: String,
    val wrappedMasterKeyBase64: String,
    val createdAtMs: Long
) {
    fun validate() {
        require(magicHeader == CLOUD_SYNC_KEY_ENVELOPE_MAGIC) {
            "Unsupported key-envelope header"
        }
        require(version == CLOUD_SYNC_KEY_ENVELOPE_VERSION) {
            "Unsupported key-envelope version: $version"
        }
        require(keyId.matches(Regex("[A-Za-z0-9_-]{16,64}"))) {
            "Invalid keyId"
        }
        require(kdf == CLOUD_SYNC_RECOVERY_KDF) {
            "Unsupported key derivation function: $kdf"
        }
        // Do not accept caller-controlled iteration counts. This prevents
        // a modified Drive file from requesting an unbounded amount of work.
        require(iterations == CLOUD_SYNC_RECOVERY_KDF_ITERATIONS) {
            "Unsupported KDF work factor"
        }
        require(saltBase64.isNotBlank()) { "Missing envelope salt" }
        require(ivBase64.isNotBlank()) { "Missing envelope IV" }
        require(wrappedMasterKeyBase64.isNotBlank()) {
            "Missing wrapped master key"
        }
        require(createdAtMs >= 0L) { "createdAtMs must be non-negative" }
    }
}
