package com.health.friday.shared.sync.cloud

import kotlinx.serialization.Serializable

const val E2EE_MAGIC_HEADER = "FRIDAY_E2EE_V1"

@Serializable
data class EncryptedSyncPayload(
    val magicHeader: String = E2EE_MAGIC_HEADER,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val hmacBase64: String
) {
    fun validate() {
        require(magicHeader == E2EE_MAGIC_HEADER) {
            "Unsupported magic header: $magicHeader"
        }
        require(saltBase64.isNotBlank()) { "saltBase64 must not be blank" }
        require(ivBase64.isNotBlank()) { "ivBase64 must not be blank" }
        require(ciphertextBase64.isNotBlank()) { "ciphertextBase64 must not be blank" }
        require(hmacBase64.isNotBlank()) { "hmacBase64 must not be blank" }
    }
}
