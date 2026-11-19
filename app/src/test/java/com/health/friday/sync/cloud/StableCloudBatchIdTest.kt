package com.health.friday.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.SyncOperation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableCloudBatchIdTest {
    private fun operation(id: String): SyncOperation {
        val device = "device_batch_test"
        return SyncOperation.newUpsert(
            deviceId = device,
            entityType = "todo",
            entityId = "todo_$id",
            hlc = HlcTimestamp(10_000L, id.last().code, device).encode(),
            createdAtMs = 10_000L,
            payload = buildJsonObject { put("id", id) }
        )
    }

    @Test
    fun batchIdentityDoesNotDependOnOutboxOrdering() {
        val a = operation("a")
        val b = operation("b")
        assertEquals(
            StableCloudBatchId.from("device_batch_test", listOf(a, b)),
            StableCloudBatchId.from("device_batch_test", listOf(b, a))
        )
    }

    @Test
    fun changingTheOperationSetChangesBatchIdentity() {
        val a = operation("a")
        val b = operation("b")
        assertNotEquals(
            StableCloudBatchId.from("device_batch_test", listOf(a)),
            StableCloudBatchId.from("device_batch_test", listOf(a, b))
        )
    }
}
