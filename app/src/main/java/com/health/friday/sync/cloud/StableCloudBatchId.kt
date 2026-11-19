package com.health.friday.sync.cloud

import com.health.friday.shared.sync.cloud.SyncOperation
import java.security.MessageDigest

/** Deterministic batch identity prevents duplicate Drive files after upload/markUploaded crashes. */
internal object StableCloudBatchId {
    fun from(deviceId: String, operations: List<SyncOperation>): String {
        require(deviceId.isNotBlank())
        require(operations.isNotEmpty())
        val canonical = buildString {
            append(deviceId)
            append('\n')
            operations.map { it.operationId }.sorted().forEach {
                append(it)
                append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
