package com.health.friday.shared.sync.cloud

import kotlinx.serialization.Serializable

const val E2EE_MAGIC_HEADER_V1 = "FRIDAY_E2EE_V1"
const val E2EE_MAGIC_HEADER_V2 = "FRIDAY_E2EE_V2"

// Kept as an alias for any code that previously imported this constant.
const val E2EE_MAGIC_HEADER = E2EE_MAGIC_HEADER_V2

@Serializable
data class EncryptedSyncPayload(
    val magicHeader: String = E2EE_MAGIC_HEADER_V2,
    // V1 fields. They remain nullable so old JSON files can still be decoded.
    val saltBase64: String? = null,
    val ivBase64: String,
    val ciphertextBase64: String,
    val hmacBase64: String? = null,
    // V2 identifies the master key required to decrypt this batch.
    val keyId: String? = null
) {
    fun validate() {
        require(ivBase64.isNotBlank()) { "ivBase64 must not be blank" }
        require(ciphertextBase64.isNotBlank()) { "ciphertextBase64 must not be blank" }

        when (magicHeader) {
            E2EE_MAGIC_HEADER_V1 -> {
                require(!saltBase64.isNullOrBlank()) {
                    "V1 payload is missing saltBase64"
                }
                require(!hmacBase64.isNullOrBlank()) {
                    "V1 payload is missing hmacBase64"
                }
            }

            E2EE_MAGIC_HEADER_V2 -> {
                require(!keyId.isNullOrBlank()) {
                    "V2 payload is missing keyId"
                }
            }

            else -> throw IllegalArgumentException(
                "Unsupported encrypted payload version: $magicHeader"
            )
        }
    }
}
