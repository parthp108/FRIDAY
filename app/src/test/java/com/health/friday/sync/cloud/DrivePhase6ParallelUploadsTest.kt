package com.health.friday.sync.cloud

import com.health.friday.shared.protocol.HlcTimestamp
import com.health.friday.shared.sync.cloud.SyncOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivePhase6ParallelUploadsTest {

    @Test
    fun testBatchChunkingLogic() {
        val validHlc = HlcTimestamp(1000L, 0, "dev1").encode()
        val dummyOps = (1..550).map { i ->
            SyncOperation.newUpsert(
                deviceId = "dev1",
                entityType = "meal",
                entityId = "id_$i",
                hlc = validHlc,
                createdAtMs = 1000L,
                payload = kotlinx.serialization.json.buildJsonObject {}
            )
        }

        val chunks = dummyOps.chunked(200)
        assertEquals(3, chunks.size)
        assertEquals(200, chunks[0].size)
        assertEquals(200, chunks[1].size)
        assertEquals(150, chunks[2].size)
    }
}
